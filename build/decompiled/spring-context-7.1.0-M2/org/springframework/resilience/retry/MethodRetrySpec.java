package org.springframework.resilience.retry;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import org.springframework.util.ExceptionTypeFilter;

public record MethodRetrySpec(
   Collection<Class<? extends Throwable>> includes,
   Collection<Class<? extends Throwable>> excludes,
   MethodRetryPredicate predicate,
   long maxRetries,
   Duration timeout,
   Duration delay,
   Duration jitter,
   double multiplier,
   Duration maxDelay
) {
   public MethodRetrySpec(MethodRetryPredicate predicate, long maxRetries, Duration delay) {
      this(predicate, maxRetries, delay, Duration.ZERO, 1.0, Duration.ofMillis(Long.MAX_VALUE));
   }

   public MethodRetrySpec(MethodRetryPredicate predicate, long maxRetries, Duration delay, Duration jitter, double multiplier, Duration maxDelay) {
      this(Collections.emptyList(), Collections.emptyList(), predicate, maxRetries, Duration.ZERO, delay, jitter, multiplier, maxDelay);
   }

   @Deprecated(since = "7.0.2", forRemoval = true)
   public MethodRetrySpec(
      Collection<Class<? extends Throwable>> includes,
      Collection<Class<? extends Throwable>> excludes,
      MethodRetryPredicate predicate,
      long maxRetries,
      Duration delay,
      Duration jitter,
      double multiplier,
      Duration maxDelay
   ) {
      this(includes, excludes, predicate, maxRetries, Duration.ZERO, delay, jitter, multiplier, maxDelay);
   }

   MethodRetryPredicate combinedPredicate() {
      ExceptionTypeFilter exceptionFilter = new ExceptionTypeFilter(this.includes, this.excludes);
      return (method, throwable) -> exceptionFilter.match(throwable, true) && this.predicate.shouldRetry(method, throwable);
   }
}
