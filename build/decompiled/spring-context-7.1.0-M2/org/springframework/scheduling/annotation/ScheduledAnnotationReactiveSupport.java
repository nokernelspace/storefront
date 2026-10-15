package org.springframework.scheduling.annotation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.support.DefaultScheduledTaskObservationConvention;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
import org.springframework.scheduling.support.ScheduledTaskObservationConvention;
import org.springframework.scheduling.support.ScheduledTaskObservationDocumentation;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

abstract class ScheduledAnnotationReactiveSupport {
   static final boolean REACTOR_PRESENT = ClassUtils.isPresent("reactor.core.publisher.Flux", ScheduledAnnotationReactiveSupport.class.getClassLoader());
   static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent(
      "kotlinx.coroutines.reactor.MonoKt", ScheduledAnnotationReactiveSupport.class.getClassLoader()
   );
   private static final Log logger = LogFactory.getLog(ScheduledAnnotationReactiveSupport.class);

   public static boolean isReactive(Method method) {
      if (KotlinDetector.isSuspendingFunction(method)) {
         Assert.isTrue(method.getParameterCount() == 1, "Kotlin suspending functions may only be annotated with @Scheduled if declared without arguments");
         Assert.isTrue(
            COROUTINES_REACTOR_PRESENT,
            "Kotlin suspending functions may only be annotated with @Scheduled if the Coroutine-Reactor bridge (kotlinx.coroutines.reactor) is present at runtime"
         );
         return true;
      }

      ReactiveAdapterRegistry registry = ReactiveAdapterRegistry.getSharedInstance();
      if (!registry.hasAdapters()) {
         return false;
      }

      Class<?> returnType = method.getReturnType();
      ReactiveAdapter candidateAdapter = registry.getAdapter(returnType);
      if (candidateAdapter == null) {
         return false;
      }

      Assert.isTrue(method.getParameterCount() == 0, "Reactive methods may only be annotated with @Scheduled if declared without arguments");
      Assert.isTrue(
         candidateAdapter.getDescriptor().isDeferred(), "Reactive methods may only be annotated with @Scheduled if the return type supports deferred execution"
      );
      return true;
   }

   public static Runnable createSubscriptionRunnable(
      Method method, Object bean, Scheduled scheduled, Supplier<ObservationRegistry> observationRegistrySupplier, List<Runnable> subscriptionTrackerRegistry
   ) {
      boolean shouldBlock = scheduled.fixedDelay() > 0L || StringUtils.hasText(scheduled.fixedDelayString());
      Publisher<?> publisher = getPublisherFor(method, bean);
      Supplier<ScheduledTaskObservationContext> contextSupplier = () -> new ScheduledTaskObservationContext(bean, method);
      String displayName = bean.getClass().getName() + "." + method.getName();
      return new ScheduledAnnotationReactiveSupport.SubscribingRunnable(
         publisher, shouldBlock, scheduled.scheduler(), subscriptionTrackerRegistry, displayName, observationRegistrySupplier, contextSupplier
      );
   }

   static Publisher<?> getPublisherFor(Method method, Object bean) {
      if (KotlinDetector.isSuspendingFunction(method)) {
         return CoroutinesUtils.invokeSuspendingFunction(method, bean, method.getParameters());
      }

      ReactiveAdapterRegistry registry = ReactiveAdapterRegistry.getSharedInstance();
      Class<?> returnType = method.getReturnType();
      ReactiveAdapter adapter = registry.getAdapter(returnType);
      if (adapter == null) {
         throw new IllegalArgumentException("Cannot convert @Scheduled reactive method return type to Publisher");
      }

      if (!adapter.getDescriptor().isDeferred()) {
         throw new IllegalArgumentException(
            "Cannot convert @Scheduled reactive method return type to Publisher: " + returnType.getSimpleName() + " is not a deferred reactive type"
         );
      }

      Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());

      try {
         ReflectionUtils.makeAccessible(invocableMethod);
         Object returnValue = invocableMethod.invoke(bean);
         Publisher<?> publisher = adapter.toPublisher(returnValue);
         return (Publisher<?>)(REACTOR_PRESENT
            ? Flux.from(publisher).checkpoint("@Scheduled '" + method.getName() + "()' in '" + method.getDeclaringClass().getName() + "'")
            : publisher);
      } catch (InvocationTargetException ex) {
         throw new IllegalArgumentException("Cannot obtain a Publisher-convertible value from the @Scheduled reactive method", ex.getTargetException());
      } catch (IllegalAccessException | InaccessibleObjectException ex) {
         throw new IllegalArgumentException("Cannot obtain a Publisher-convertible value from the @Scheduled reactive method", ex);
      }
   }

   static final class SubscribingRunnable implements SchedulingAwareRunnable {
      private static final ScheduledTaskObservationConvention DEFAULT_CONVENTION = new DefaultScheduledTaskObservationConvention();
      private final Publisher<?> publisher;
      final boolean shouldBlock;
      final String displayName;
      private final @Nullable String qualifier;
      private final List<Runnable> subscriptionTrackerRegistry;
      final Supplier<ObservationRegistry> observationRegistrySupplier;
      final Supplier<ScheduledTaskObservationContext> contextSupplier;

      SubscribingRunnable(
         Publisher<?> publisher,
         boolean shouldBlock,
         @Nullable String qualifier,
         List<Runnable> subscriptionTrackerRegistry,
         String displayName,
         Supplier<ObservationRegistry> observationRegistrySupplier,
         Supplier<ScheduledTaskObservationContext> contextSupplier
      ) {
         this.publisher = publisher;
         this.shouldBlock = shouldBlock;
         this.displayName = displayName;
         this.qualifier = qualifier;
         this.subscriptionTrackerRegistry = subscriptionTrackerRegistry;
         this.observationRegistrySupplier = observationRegistrySupplier;
         this.contextSupplier = contextSupplier;
      }

      @Override
      public @Nullable String getQualifier() {
         return this.qualifier;
      }

      @Override
      public void run() {
         Observation observation = ScheduledTaskObservationDocumentation.TASKS_SCHEDULED_EXECUTION
            .observation(null, DEFAULT_CONVENTION, this.contextSupplier, this.observationRegistrySupplier.get());
         if (this.shouldBlock) {
            CountDownLatch latch = new CountDownLatch(1);
            ScheduledAnnotationReactiveSupport.TrackingSubscriber subscriber = new ScheduledAnnotationReactiveSupport.TrackingSubscriber(
               this.subscriptionTrackerRegistry, observation, latch
            );
            this.subscribe(subscriber, observation);

            try {
               latch.await();
            } catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         } else {
            ScheduledAnnotationReactiveSupport.TrackingSubscriber subscriber = new ScheduledAnnotationReactiveSupport.TrackingSubscriber(
               this.subscriptionTrackerRegistry, observation
            );
            this.subscribe(subscriber, observation);
         }
      }

      private void subscribe(ScheduledAnnotationReactiveSupport.TrackingSubscriber subscriber, Observation observation) {
         this.subscriptionTrackerRegistry.add(subscriber);
         if (ScheduledAnnotationReactiveSupport.REACTOR_PRESENT) {
            observation.start();
            Flux.from(this.publisher).contextWrite(context -> context.put("micrometer.observation", observation)).subscribe(subscriber);
         } else {
            this.publisher.subscribe(subscriber);
         }
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   private static final class TrackingSubscriber implements Subscriber<Object>, Runnable {
      private final List<Runnable> subscriptionTrackerRegistry;
      private final Observation observation;
      private final @Nullable CountDownLatch blockingLatch;
      private @Nullable Subscription subscription;

      TrackingSubscriber(List<Runnable> subscriptionTrackerRegistry, Observation observation) {
         this(subscriptionTrackerRegistry, observation, null);
      }

      TrackingSubscriber(List<Runnable> subscriptionTrackerRegistry, Observation observation, @Nullable CountDownLatch latch) {
         this.subscriptionTrackerRegistry = subscriptionTrackerRegistry;
         this.observation = observation;
         this.blockingLatch = latch;
      }

      @Override
      public void run() {
         if (this.subscription != null) {
            this.subscription.cancel();
            this.observation.stop();
         }

         if (this.blockingLatch != null) {
            this.blockingLatch.countDown();
         }
      }

      public void onSubscribe(Subscription subscription) {
         this.subscription = subscription;
         subscription.request(2147483647L);
      }

      public void onNext(Object obj) {
      }

      public void onError(Throwable ex) {
         this.subscriptionTrackerRegistry.remove(this);
         ScheduledAnnotationReactiveSupport.logger.warn("Unexpected error occurred in scheduled reactive task", ex);
         this.observation.error(ex);
         this.observation.stop();
         if (this.blockingLatch != null) {
            this.blockingLatch.countDown();
         }
      }

      public void onComplete() {
         this.subscriptionTrackerRegistry.remove(this);
         if (this.observation.getContext() instanceof ScheduledTaskObservationContext context) {
            context.setComplete(true);
         }

         this.observation.stop();
         if (this.blockingLatch != null) {
            this.blockingLatch.countDown();
         }
      }
   }
}
