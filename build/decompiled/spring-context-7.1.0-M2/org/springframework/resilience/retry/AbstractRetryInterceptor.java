package org.springframework.resilience.retry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Future;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public abstract class AbstractRetryInterceptor implements MethodInterceptor, ApplicationEventPublisherAware {
   private static final Log logger = LogFactory.getLog(AbstractRetryInterceptor.class);
   private static final boolean REACTIVE_STREAMS_PRESENT = ClassUtils.isPresent(
      "org.reactivestreams.Publisher", AbstractRetryInterceptor.class.getClassLoader()
   );
   private final @Nullable ReactiveAdapterRegistry reactiveAdapterRegistry;
   private @Nullable ApplicationEventPublisher applicationEventPublisher;

   public AbstractRetryInterceptor() {
      if (REACTIVE_STREAMS_PRESENT) {
         this.reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
      } else {
         this.reactiveAdapterRegistry = null;
      }
   }

   @Override
   public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
      this.applicationEventPublisher = applicationEventPublisher;
   }

   public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
      Method method = invocation.getMethod();
      Object target = invocation.getThis();
      MethodRetrySpec spec = this.getRetrySpec(method, target != null ? target.getClass() : method.getDeclaringClass());
      if (spec == null) {
         return invocation.proceed();
      }

      if (this.reactiveAdapterRegistry != null && !Future.class.isAssignableFrom(method.getReturnType())) {
         ReactiveAdapter adapter = this.reactiveAdapterRegistry.getAdapter(method.getReturnType());
         if (adapter != null) {
            Object result = invocation.proceed();
            if (result == null) {
               return null;
            }

            return new AbstractRetryInterceptor.ReactorDelegate().adaptReactiveResult(invocation, result, adapter, spec);
         }
      }

      RetryPolicy retryPolicy = RetryPolicy.builder()
         .includes(spec.includes())
         .excludes(spec.excludes())
         .predicate(spec.predicate().forMethod(method))
         .maxRetries(spec.maxRetries())
         .timeout(spec.timeout())
         .delay(spec.delay())
         .jitter(spec.jitter())
         .multiplier(spec.multiplier())
         .maxDelay(spec.maxDelay())
         .build();
      RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
      retryTemplate.setRetryListener(new RetryListener() {
         public void onRetryableExecution(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
            if (!retryState.isSuccessful()) {
               AbstractRetryInterceptor.this.onEvent(new MethodRetryEvent(invocation, retryState.getLastException(), false));
            }
         }
      });
      final String methodName = ClassUtils.getQualifiedMethodName(method, target != null ? target.getClass() : null);

      try {
         return retryTemplate.execute(new Retryable<Object>() {
            public @Nullable Object execute() throws Throwable {
               return invocation instanceof ProxyMethodInvocation pmi ? pmi.invocableClone().proceed() : invocation.proceed();
            }

            public String getName() {
               return methodName;
            }
         });
      } catch (RetryException ex) {
         this.onEvent(new MethodRetryEvent(invocation, ex, true));
         if (logger.isDebugEnabled()) {
            logger.debug("Retryable operation '%s' failed".formatted(methodName), ex);
         }

         throw ex.getCause();
      }
   }

   private void onEvent(MethodRetryEvent event) {
      logger.trace(event, event.getFailure());
      if (this.applicationEventPublisher != null) {
         this.applicationEventPublisher.publishEvent(event);
      }
   }

   protected abstract @Nullable MethodRetrySpec getRetrySpec(Method method, Class<?> targetClass);

   private class ReactorDelegate {
      public Object adaptReactiveResult(MethodInvocation invocation, Object result, ReactiveAdapter adapter, MethodRetrySpec spec) {
         Publisher<?> publisher = adapter.toPublisher(result);
         Retry retry = Retry.backoff(spec.maxRetries(), spec.delay())
            .jitter(calculateJitterFactor(spec))
            .multiplier(spec.multiplier())
            .maxBackoff(spec.maxDelay())
            .filter(spec.combinedPredicate().forMethod(invocation.getMethod()));
         Duration timeout = spec.timeout();
         boolean timeoutIsPositive = !timeout.isNegative() && !timeout.isZero();
         if (adapter.isMultiValue()) {
            Flux<?> flux = Flux.from(publisher)
               .doOnError(ex -> AbstractRetryInterceptor.this.onEvent(new MethodRetryEvent(invocation, ex, false)))
               .retryWhen(retry);
            if (timeoutIsPositive) {
               flux = flux.timeout(timeout);
            }

            flux = flux.doOnError(ex -> AbstractRetryInterceptor.this.onEvent(new MethodRetryEvent(invocation, ex, true)));
            publisher = flux;
         } else {
            Mono<?> mono = Mono.from(publisher)
               .doOnError(ex -> AbstractRetryInterceptor.this.onEvent(new MethodRetryEvent(invocation, ex, false)))
               .retryWhen(retry);
            if (timeoutIsPositive) {
               mono = mono.timeout(timeout);
            }

            mono = mono.doOnError(ex -> AbstractRetryInterceptor.this.onEvent(new MethodRetryEvent(invocation, ex, true)));
            publisher = mono;
         }

         return adapter.fromPublisher(publisher);
      }

      private static double calculateJitterFactor(MethodRetrySpec spec) {
         return spec.delay().isZero() ? 0.0 : Math.max(0.0, Math.min(1.0, (double)spec.jitter().toNanos() / spec.delay().toNanos()));
      }
   }
}
