package org.springframework.core;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.MultiCreate;
import io.smallrye.mutiny.groups.UniConvert;
import io.smallrye.mutiny.groups.UniCreate;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Function;
import kotlinx.coroutines.CompletableDeferredKt;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import kotlinx.coroutines.reactor.ReactorFlowKt;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import reactor.adapter.JdkFlowAdapter;
import reactor.blockhound.BlockHound.Builder;
import reactor.blockhound.integration.BlockHoundIntegration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveAdapterRegistry {
   private static volatile @Nullable ReactiveAdapterRegistry sharedInstance;
   private static final boolean REACTIVE_STREAMS_PRESENT;
   private static final boolean REACTOR_PRESENT;
   private static final boolean RXJAVA_3_PRESENT;
   private static final boolean COROUTINES_REACTOR_PRESENT;
   private static final boolean MUTINY_PRESENT;
   private static final boolean CONTEXT_PROPAGATION_PRESENT;
   private final List<ReactiveAdapter> adapters = new ArrayList<>();

   public ReactiveAdapterRegistry() {
      if (REACTIVE_STREAMS_PRESENT) {
         if (REACTOR_PRESENT) {
            new ReactiveAdapterRegistry.ReactorRegistrar().registerAdapters(this);
         }

         if (RXJAVA_3_PRESENT) {
            new ReactiveAdapterRegistry.RxJava3Registrar().registerAdapters(this);
         }

         if (REACTOR_PRESENT && COROUTINES_REACTOR_PRESENT) {
            new ReactiveAdapterRegistry.CoroutinesRegistrar().registerAdapters(this);
         }

         if (MUTINY_PRESENT) {
            new ReactiveAdapterRegistry.MutinyRegistrar().registerAdapters(this);
         }

         if (!REACTOR_PRESENT) {
            new ReactiveAdapterRegistry.FlowAdaptersRegistrar().registerAdapters(this);
         }
      }
   }

   public void registerReactiveType(ReactiveTypeDescriptor descriptor, Function<Object, Publisher<?>> toAdapter, Function<Publisher<?>, Object> fromAdapter) {
      this.adapters.add(this.buildAdapter(descriptor, toAdapter, fromAdapter));
   }

   public void registerReactiveTypeOverride(
      ReactiveTypeDescriptor descriptor, Function<Object, Publisher<?>> toAdapter, Function<Publisher<?>, Object> fromAdapter
   ) {
      this.adapters.add(0, this.buildAdapter(descriptor, toAdapter, fromAdapter));
   }

   private ReactiveAdapter buildAdapter(ReactiveTypeDescriptor descriptor, Function<Object, Publisher<?>> toAdapter, Function<Publisher<?>, Object> fromAdapter) {
      return REACTOR_PRESENT
         ? new ReactiveAdapterRegistry.ReactorAdapter(descriptor, toAdapter, fromAdapter)
         : new ReactiveAdapter(descriptor, toAdapter, fromAdapter);
   }

   public boolean hasAdapters() {
      return !this.adapters.isEmpty();
   }

   public @Nullable ReactiveAdapter getAdapter(Class<?> reactiveType) {
      return this.getAdapter(reactiveType, null);
   }

   public @Nullable ReactiveAdapter getAdapter(@Nullable Class<?> reactiveType, @Nullable Object source) {
      if (this.adapters.isEmpty()) {
         return null;
      }

      Object sourceToUse = source instanceof Optional<?> optional ? optional.orElse(null) : source;
      Class<?> clazz = sourceToUse != null ? sourceToUse.getClass() : reactiveType;
      if (clazz == null) {
         return null;
      }

      for (ReactiveAdapter adapter : this.adapters) {
         if (adapter.getReactiveType() == clazz) {
            return adapter;
         }
      }

      for (ReactiveAdapter adapter : this.adapters) {
         if (adapter.getReactiveType().isAssignableFrom(clazz)) {
            return adapter;
         }
      }

      return null;
   }

   public static ReactiveAdapterRegistry getSharedInstance() {
      ReactiveAdapterRegistry registry = sharedInstance;
      if (registry == null) {
         synchronized (ReactiveAdapterRegistry.class) {
            registry = sharedInstance;
            if (registry == null) {
               registry = new ReactiveAdapterRegistry();
               sharedInstance = registry;
            }
         }
      }

      return registry;
   }

   static {
      ClassLoader classLoader = ReactiveAdapterRegistry.class.getClassLoader();
      REACTIVE_STREAMS_PRESENT = ClassUtils.isPresent("org.reactivestreams.Publisher", classLoader);
      REACTOR_PRESENT = ClassUtils.isPresent("reactor.core.publisher.Flux", classLoader);
      RXJAVA_3_PRESENT = ClassUtils.isPresent("io.reactivex.rxjava3.core.Flowable", classLoader);
      COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt", classLoader);
      MUTINY_PRESENT = ClassUtils.isPresent("io.smallrye.mutiny.Multi", classLoader);
      CONTEXT_PROPAGATION_PRESENT = ClassUtils.isPresent("io.micrometer.context.ContextSnapshotFactory", classLoader);
   }

   private static class CoroutinesRegistrar {
      void registerAdapters(ReactiveAdapterRegistry registry) {
         registry.registerReactiveType(
            ReactiveTypeDescriptor.singleOptionalValue(Deferred.class, () -> CompletableDeferredKt.CompletableDeferred(null)),
            source -> CoroutinesUtils.deferredToMono((Deferred)source),
            source -> CoroutinesUtils.monoToDeferred(Mono.from(source))
         );
         registry.registerReactiveType(
            ReactiveTypeDescriptor.multiValue(Flow.class, FlowKt::emptyFlow),
            ReactiveAdapterRegistry.CONTEXT_PROPAGATION_PRESENT
               ? source -> ReactorFlowKt.asFlux((Flow)source, new PropagationContextElement())
               : source -> ReactorFlowKt.asFlux((Flow)source),
            ReactiveFlowKt::asFlow
         );
      }
   }

   private static class EmptyCompletableFuture<T> extends CompletableFuture<T> {
      EmptyCompletableFuture() {
         this.complete(null);
      }
   }

   private static class FlowAdaptersRegistrar {
      private static final Subscription EMPTY_SUBSCRIPTION = new Subscription() {
         @Override
         public void request(long n) {
         }

         @Override
         public void cancel() {
         }
      };
      private static final java.util.concurrent.Flow.Publisher<Object> EMPTY_PUBLISHER = subscriber -> {
         subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
         subscriber.onComplete();
      };

      void registerAdapters(ReactiveAdapterRegistry registry) {
         registry.registerReactiveType(
            ReactiveTypeDescriptor.multiValue(java.util.concurrent.Flow.Publisher.class, () -> EMPTY_PUBLISHER),
            source -> FlowAdapters.toPublisher((java.util.concurrent.Flow.Publisher)source),
            source -> FlowAdapters.toFlowPublisher(source)
         );
      }
   }

   private static class MutinyRegistrar {
      private static final Method uniToPublisher = ClassUtils.getMethod(UniConvert.class, "toPublisher");

      void registerAdapters(ReactiveAdapterRegistry registry) {
         ReactiveTypeDescriptor uniDesc = ReactiveTypeDescriptor.singleOptionalValue(Uni.class, () -> Uni.createFrom().nullItem());
         ReactiveTypeDescriptor multiDesc = ReactiveTypeDescriptor.multiValue(Multi.class, () -> Multi.createFrom().empty());
         if (java.util.concurrent.Flow.Publisher.class.isAssignableFrom(uniToPublisher.getReturnType())) {
            Method uniPublisher = ClassUtils.getMethod(UniCreate.class, "publisher", java.util.concurrent.Flow.Publisher.class);
            Method multiPublisher = ClassUtils.getMethod(MultiCreate.class, "publisher", java.util.concurrent.Flow.Publisher.class);
            registry.registerReactiveType(
               uniDesc,
               uni -> FlowAdapters.toPublisher(
                  Objects.requireNonNull((java.util.concurrent.Flow.Publisher)ReflectionUtils.invokeMethod(uniToPublisher, ((Uni)uni).convert()))
               ),
               publisher -> Objects.requireNonNull(ReflectionUtils.invokeMethod(uniPublisher, Uni.createFrom(), FlowAdapters.toFlowPublisher(publisher)))
            );
            registry.registerReactiveType(
               multiDesc,
               multi -> FlowAdapters.toPublisher((java.util.concurrent.Flow.Publisher)multi),
               publisher -> Objects.requireNonNull(ReflectionUtils.invokeMethod(multiPublisher, Multi.createFrom(), FlowAdapters.toFlowPublisher(publisher)))
            );
         } else {
            registry.registerReactiveType(uniDesc, uni -> ((Uni)uni).convert().toPublisher(), publisher -> Uni.createFrom().publisher(publisher));
            registry.registerReactiveType(multiDesc, multi -> (Multi)multi, publisher -> Multi.createFrom().publisher(publisher));
         }
      }
   }

   private static class ReactorAdapter extends ReactiveAdapter {
      ReactorAdapter(
         ReactiveTypeDescriptor descriptor, Function<Object, Publisher<?>> toPublisherFunction, Function<Publisher<?>, Object> fromPublisherFunction
      ) {
         super(descriptor, toPublisherFunction, fromPublisherFunction);
      }

      @Override
      public <T> Publisher<T> toPublisher(@Nullable Object source) {
         Publisher<T> publisher = super.toPublisher(source);
         return (Publisher<T>)(this.isMultiValue() ? Flux.from(publisher) : Mono.from(publisher));
      }
   }

   private static class ReactorRegistrar {
      private static final java.util.concurrent.Flow.Publisher<?> EMPTY_FLOW = JdkFlowAdapter.publisherToFlowPublisher(Flux.empty());

      void registerAdapters(ReactiveAdapterRegistry registry) {
         registry.registerReactiveType(ReactiveTypeDescriptor.singleOptionalValue(Mono.class, Mono::empty), source -> (Mono)source, Mono::from);
         registry.registerReactiveType(ReactiveTypeDescriptor.multiValue(Flux.class, Flux::empty), source -> (Flux)source, Flux::from);
         registry.registerReactiveType(ReactiveTypeDescriptor.multiValue(Publisher.class, Flux::empty), source -> (Publisher<?>)source, source -> source);
         registry.registerReactiveType(
            ReactiveTypeDescriptor.nonDeferredAsyncValue(CompletionStage.class, ReactiveAdapterRegistry.EmptyCompletableFuture::new),
            source -> Mono.fromCompletionStage((CompletionStage)source),
            source -> Mono.from(source).toFuture()
         );
         registry.registerReactiveType(
            ReactiveTypeDescriptor.multiValue(java.util.concurrent.Flow.Publisher.class, () -> EMPTY_FLOW),
            source -> JdkFlowAdapter.flowPublisherToFlux((java.util.concurrent.Flow.Publisher)source),
            JdkFlowAdapter::publisherToFlowPublisher
         );
      }
   }

   private static class RxJava3Registrar {
      void registerAdapters(ReactiveAdapterRegistry registry) {
         registry.registerReactiveType(ReactiveTypeDescriptor.multiValue(Flowable.class, Flowable::empty), source -> (Flowable)source, Flowable::fromPublisher);
         registry.registerReactiveType(
            ReactiveTypeDescriptor.multiValue(Observable.class, Observable::empty),
            source -> ((Observable)source).toFlowable(BackpressureStrategy.BUFFER),
            Observable::fromPublisher
         );
         registry.registerReactiveType(ReactiveTypeDescriptor.singleRequiredValue(Single.class), source -> ((Single)source).toFlowable(), Single::fromPublisher);
         registry.registerReactiveType(
            ReactiveTypeDescriptor.singleOptionalValue(Maybe.class, Maybe::empty), source -> ((Maybe)source).toFlowable(), Maybe::fromPublisher
         );
         registry.registerReactiveType(
            ReactiveTypeDescriptor.noValue(Completable.class, Completable::complete), source -> ((Completable)source).toFlowable(), Completable::fromPublisher
         );
      }
   }

   public static class SpringCoreBlockHoundIntegration implements BlockHoundIntegration {
      public void applyTo(Builder builder) {
         String segmentClassName = "org.springframework.util.ConcurrentReferenceHashMap$Segment";
         builder.allowBlockingCallsInside(segmentClassName, "doTask");
         builder.allowBlockingCallsInside(segmentClassName, "clear");
         builder.allowBlockingCallsInside(segmentClassName, "restructure");
         String referenceManagerClassName = "org.springframework.util.ConcurrentReferenceHashMap$ReferenceManager";
         builder.allowBlockingCallsInside(referenceManagerClassName, "pollForPurge");
      }
   }
}
