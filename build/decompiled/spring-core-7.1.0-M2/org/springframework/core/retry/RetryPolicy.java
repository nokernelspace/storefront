package org.springframework.core.retry;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

public interface RetryPolicy {
   boolean shouldRetry(Throwable throwable);

   default Duration getTimeout() {
      return RetryPolicy.Builder.DEFAULT_TIMEOUT;
   }

   default BackOff getBackOff() {
      return new FixedBackOff(1000L, 3L);
   }

   static RetryPolicy withDefaults() {
      return throwable -> true;
   }

   static RetryPolicy withMaxRetries(long maxRetries) {
      assertMaxRetriesIsNotNegative(maxRetries);
      return builder().backOff(new FixedBackOff(1000L, maxRetries)).build();
   }

   static RetryPolicy.Builder builder() {
      return new RetryPolicy.Builder();
   }

   private static void assertMaxRetriesIsNotNegative(long maxRetries) {
      Assert.isTrue(maxRetries >= 0L, () -> "Invalid maxRetries (%d): must be positive or zero for no retry.".formatted(maxRetries));
   }

   private static void assertIsNotNegative(String name, Duration duration) {
      Assert.isTrue(!duration.isNegative(), () -> "Invalid %s (%dms): must be greater than or equal to zero.".formatted(name, duration.toMillis()));
   }

   private static void assertIsPositive(String name, Duration duration) {
      Assert.isTrue(!duration.isNegative() && !duration.isZero(), () -> "Invalid %s (%dms): must be greater than zero.".formatted(name, duration.toMillis()));
   }

   final class Builder {
      public static final long DEFAULT_MAX_RETRIES = 3L;
      public static final long DEFAULT_DELAY = 1000L;
      public static final long DEFAULT_JITTER = 0L;
      public static final double DEFAULT_MULTIPLIER = 1.0;
      public static final long DEFAULT_MAX_DELAY = Long.MAX_VALUE;
      public static final Duration DEFAULT_TIMEOUT = Duration.ZERO;
      private @Nullable BackOff backOff;
      private @Nullable Long maxRetries;
      private @Nullable Duration delay;
      private @Nullable Duration jitter;
      private @Nullable Double multiplier;
      private @Nullable Duration maxDelay;
      private Duration timeout = DEFAULT_TIMEOUT;
      private final Set<Class<? extends Throwable>> includes = new LinkedHashSet<>();
      private final Set<Class<? extends Throwable>> excludes = new LinkedHashSet<>();
      private @Nullable Predicate<Throwable> predicate;

      private Builder() {
      }

      public RetryPolicy.Builder backOff(BackOff backOff) {
         Assert.notNull(backOff, "BackOff must not be null");
         this.backOff = backOff;
         return this;
      }

      public RetryPolicy.Builder maxRetries(long maxRetries) {
         RetryPolicy.assertMaxRetriesIsNotNegative(maxRetries);
         this.maxRetries = maxRetries;
         return this;
      }

      public RetryPolicy.Builder delay(Duration delay) {
         RetryPolicy.assertIsNotNegative("delay", delay);
         this.delay = delay;
         return this;
      }

      public RetryPolicy.Builder jitter(Duration jitter) {
         RetryPolicy.assertIsNotNegative("jitter", jitter);
         this.jitter = jitter;
         return this;
      }

      public RetryPolicy.Builder multiplier(double multiplier) {
         Assert.isTrue(
            multiplier >= 1.0,
            () -> "Invalid multiplier '" + multiplier + "': must be greater than or equal to 1. A multiplier of 1 is equivalent to a fixed delay."
         );
         this.multiplier = multiplier;
         return this;
      }

      public RetryPolicy.Builder maxDelay(Duration maxDelay) {
         RetryPolicy.assertIsPositive("maxDelay", maxDelay);
         this.maxDelay = maxDelay;
         return this;
      }

      public RetryPolicy.Builder timeout(Duration timeout) {
         RetryPolicy.assertIsNotNegative("timeout", timeout);
         this.timeout = timeout;
         return this;
      }

      @SafeVarargs
      public final RetryPolicy.Builder includes(Class<? extends Throwable>... types) {
         Collections.addAll(this.includes, types);
         return this;
      }

      public RetryPolicy.Builder includes(Collection<Class<? extends Throwable>> types) {
         this.includes.addAll(types);
         return this;
      }

      @SafeVarargs
      public final RetryPolicy.Builder excludes(Class<? extends Throwable>... types) {
         Collections.addAll(this.excludes, types);
         return this;
      }

      public RetryPolicy.Builder excludes(Collection<Class<? extends Throwable>> types) {
         this.excludes.addAll(types);
         return this;
      }

      public RetryPolicy.Builder predicate(Predicate<Throwable> predicate) {
         this.predicate = this.predicate != null ? this.predicate.and(predicate) : predicate;
         return this;
      }

      public RetryPolicy build() {
         BackOff backOff = this.backOff;
         if (backOff != null) {
            boolean misconfigured = this.maxRetries != null || this.delay != null || this.jitter != null || this.multiplier != null || this.maxDelay != null;
            Assert.state(
               !misconfigured,
               "The following configuration options are not supported with a custom BackOff strategy: maxRetries, delay, jitter, multiplier, or maxDelay."
            );
         } else {
            ExponentialBackOff exponentialBackOff = new ExponentialBackOff();
            exponentialBackOff.setMaxAttempts(this.maxRetries != null ? this.maxRetries : 3L);
            exponentialBackOff.setInitialInterval(this.delay != null ? this.delay.toMillis() : 1000L);
            exponentialBackOff.setJitter(this.jitter != null ? this.jitter.toMillis() : 0L);
            exponentialBackOff.setMultiplier(this.multiplier != null ? this.multiplier : 1.0);
            exponentialBackOff.setMaxInterval(this.maxDelay != null ? this.maxDelay.toMillis() : Long.MAX_VALUE);
            backOff = exponentialBackOff;
         }

         return new DefaultRetryPolicy(this.includes, this.excludes, this.predicate, this.timeout, backOff);
      }
   }
}
