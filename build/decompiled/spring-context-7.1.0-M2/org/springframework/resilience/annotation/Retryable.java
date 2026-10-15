package org.springframework.resilience.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;
import org.springframework.resilience.retry.MethodRetryPredicate;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Reflective
public @interface Retryable {
   @AliasFor("includes")
   Class<? extends Throwable>[] value() default {};

   @AliasFor("value")
   Class<? extends Throwable>[] includes() default {};

   Class<? extends Throwable>[] excludes() default {};

   Class<? extends MethodRetryPredicate> predicate() default MethodRetryPredicate.class;

   long maxRetries() default 3L;

   String maxRetriesString() default "";

   long timeout() default 0L;

   String timeoutString() default "";

   long delay() default 1000L;

   String delayString() default "";

   long jitter() default 0L;

   String jitterString() default "";

   double multiplier() default 1.0;

   String multiplierString() default "";

   long maxDelay() default Long.MAX_VALUE;

   String maxDelayString() default "";

   TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
