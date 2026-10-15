package org.springframework.resilience.annotation;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import org.springframework.resilience.retry.AbstractRetryInterceptor;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.resilience.retry.MethodRetrySpec;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public class RetryAnnotationBeanPostProcessor
   extends AbstractBeanFactoryAwareAdvisingPostProcessor
   implements ApplicationEventPublisherAware,
   EmbeddedValueResolverAware {
   private final RetryAnnotationBeanPostProcessor.RetryAnnotationInterceptor interceptor = new RetryAnnotationBeanPostProcessor.RetryAnnotationInterceptor();
   private @Nullable StringValueResolver embeddedValueResolver;

   public RetryAnnotationBeanPostProcessor() {
      this.setBeforeExistingAdvisors(true);
      Pointcut cpc = new AnnotationMatchingPointcut(Retryable.class, true);
      Pointcut mpc = new AnnotationMatchingPointcut(null, Retryable.class, true);
      this.advisor = new DefaultPointcutAdvisor(new ComposablePointcut(cpc).union(mpc), this.interceptor);
   }

   @Override
   public void setEmbeddedValueResolver(StringValueResolver resolver) {
      this.embeddedValueResolver = resolver;
   }

   @Override
   public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
      this.interceptor.setApplicationEventPublisher(applicationEventPublisher);
   }

   private class RetryAnnotationInterceptor extends AbstractRetryInterceptor {
      private final Map<MethodClassKey, MethodRetrySpec> retrySpecCache = new ConcurrentHashMap<>();

      @Override
      protected @Nullable MethodRetrySpec getRetrySpec(Method method, Class<?> targetClass) {
         MethodClassKey cacheKey = new MethodClassKey(method, targetClass);
         MethodRetrySpec retrySpec = this.retrySpecCache.get(cacheKey);
         if (retrySpec != null) {
            return retrySpec;
         }

         Retryable retryable = (Retryable)AnnotatedElementUtils.findMergedAnnotation(method, Retryable.class);
         if (retryable == null) {
            retryable = (Retryable)AnnotatedElementUtils.findMergedAnnotation(targetClass, Retryable.class);
            if (retryable == null) {
               return null;
            }
         }

         TimeUnit timeUnit = retryable.timeUnit();
         retrySpec = new MethodRetrySpec(
            Arrays.asList(retryable.includes()),
            Arrays.asList(retryable.excludes()),
            this.instantiatePredicate(retryable.predicate()),
            this.parseLong(retryable.maxRetries(), retryable.maxRetriesString()),
            this.parseDuration(retryable.timeout(), retryable.timeoutString(), timeUnit),
            this.parseDuration(retryable.delay(), retryable.delayString(), timeUnit),
            this.parseDuration(retryable.jitter(), retryable.jitterString(), timeUnit),
            this.parseDouble(retryable.multiplier(), retryable.multiplierString()),
            this.parseDuration(retryable.maxDelay(), retryable.maxDelayString(), timeUnit)
         );
         MethodRetrySpec existing = this.retrySpecCache.putIfAbsent(cacheKey, retrySpec);
         return existing != null ? existing : retrySpec;
      }

      private MethodRetryPredicate instantiatePredicate(Class<? extends MethodRetryPredicate> predicateClass) {
         if (predicateClass == MethodRetryPredicate.class) {
            return (method, throwable) -> true;
         }

         try {
            return RetryAnnotationBeanPostProcessor.this.beanFactory != null
               ? (MethodRetryPredicate)RetryAnnotationBeanPostProcessor.this.beanFactory.createBean(predicateClass)
               : (MethodRetryPredicate)ReflectionUtils.accessibleConstructor(predicateClass, new Class[0]).newInstance();
         } catch (Throwable ex) {
            throw new IllegalStateException("Failed to instantiate predicate class [" + predicateClass + "]", ex);
         }
      }

      private long parseLong(long value, String stringValue) {
         if (StringUtils.hasText(stringValue)) {
            if (RetryAnnotationBeanPostProcessor.this.embeddedValueResolver != null) {
               stringValue = RetryAnnotationBeanPostProcessor.this.embeddedValueResolver.resolveStringValue(stringValue);
            }

            if (StringUtils.hasText(stringValue)) {
               return Long.parseLong(stringValue);
            }
         }

         return value;
      }

      private double parseDouble(double value, String stringValue) {
         if (StringUtils.hasText(stringValue)) {
            if (RetryAnnotationBeanPostProcessor.this.embeddedValueResolver != null) {
               stringValue = RetryAnnotationBeanPostProcessor.this.embeddedValueResolver.resolveStringValue(stringValue);
            }

            if (StringUtils.hasText(stringValue)) {
               return Double.parseDouble(stringValue);
            }
         }

         return value;
      }

      private Duration parseDuration(long value, String stringValue, TimeUnit timeUnit) {
         if (StringUtils.hasText(stringValue)) {
            if (RetryAnnotationBeanPostProcessor.this.embeddedValueResolver != null) {
               stringValue = RetryAnnotationBeanPostProcessor.this.embeddedValueResolver.resolveStringValue(stringValue);
            }

            if (StringUtils.hasText(stringValue)) {
               return toDuration(stringValue, timeUnit);
            }
         }

         return toDuration(value, timeUnit);
      }

      private static Duration toDuration(long value, TimeUnit timeUnit) {
         return Duration.of(value, timeUnit.toChronoUnit());
      }

      private static Duration toDuration(String value, TimeUnit timeUnit) {
         DurationFormat.Unit unit = DurationFormat.Unit.fromChronoUnit(timeUnit.toChronoUnit());
         return DurationFormatterUtils.detectAndParse(value, unit);
      }
   }
}
