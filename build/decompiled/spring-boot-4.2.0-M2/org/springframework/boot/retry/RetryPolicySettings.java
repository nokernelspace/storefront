package org.springframework.boot.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryPolicy.Builder;

public final class RetryPolicySettings {
   public static final long DEFAULT_MAX_RETRIES = 3L;
   public static final Duration DEFAULT_TIMEOUT = Builder.DEFAULT_TIMEOUT;
   public static final Duration DEFAULT_DELAY = Duration.ofMillis(1000L);
   public static final double DEFAULT_MULTIPLIER = 1.0;
   public static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(Long.MAX_VALUE);
   private List<Class<? extends Throwable>> exceptionIncludes = new ArrayList<>();
   private List<Class<? extends Throwable>> exceptionExcludes = new ArrayList<>();
   private @Nullable Predicate<Throwable> exceptionPredicate;
   private Long maxRetries = 3L;
   private Duration timeout = DEFAULT_TIMEOUT;
   private Duration delay = DEFAULT_DELAY;
   private @Nullable Duration jitter;
   private Double multiplier = 1.0;
   private Duration maxDelay = DEFAULT_MAX_DELAY;
   private @Nullable Function<Builder, RetryPolicy> factory;

   public RetryPolicy createRetryPolicy() {
      PropertyMapper map = PropertyMapper.get();
      Builder builder = RetryPolicy.builder();
      map.from(this::getExceptionIncludes).to(builder::includes);
      map.from(this::getExceptionExcludes).to(builder::excludes);
      map.from(this::getExceptionPredicate).to(builder::predicate);
      map.from(this::getMaxRetries).to(builder::maxRetries);
      map.from(this::getTimeout).to(builder::timeout);
      map.from(this::getDelay).to(builder::delay);
      map.from(this::getJitter).to(builder::jitter);
      map.from(this::getMultiplier).to(builder::multiplier);
      map.from(this::getMaxDelay).to(builder::maxDelay);
      return this.factory != null ? this.factory.apply(builder) : builder.build();
   }

   public List<Class<? extends Throwable>> getExceptionIncludes() {
      return this.exceptionIncludes;
   }

   public void setExceptionIncludes(List<Class<? extends Throwable>> includes) {
      this.exceptionIncludes = new ArrayList<>(includes);
   }

   public List<Class<? extends Throwable>> getExceptionExcludes() {
      return this.exceptionExcludes;
   }

   public void setExceptionExcludes(List<Class<? extends Throwable>> excludes) {
      this.exceptionExcludes = new ArrayList<>(excludes);
   }

   public @Nullable Predicate<Throwable> getExceptionPredicate() {
      return this.exceptionPredicate;
   }

   public void setExceptionPredicate(@Nullable Predicate<Throwable> exceptionPredicate) {
      this.exceptionPredicate = exceptionPredicate;
   }

   public Long getMaxRetries() {
      return this.maxRetries;
   }

   public void setMaxRetries(Long maxRetries) {
      this.maxRetries = maxRetries;
   }

   public Duration getTimeout() {
      return this.timeout;
   }

   public void setTimeout(Duration timeout) {
      this.timeout = timeout;
   }

   public Duration getDelay() {
      return this.delay;
   }

   public void setDelay(Duration delay) {
      this.delay = delay;
   }

   public @Nullable Duration getJitter() {
      return this.jitter;
   }

   public void setJitter(@Nullable Duration jitter) {
      this.jitter = jitter;
   }

   public Double getMultiplier() {
      return this.multiplier;
   }

   public void setMultiplier(Double multiplier) {
      this.multiplier = multiplier;
   }

   public Duration getMaxDelay() {
      return this.maxDelay;
   }

   public void setMaxDelay(Duration maxDelay) {
      this.maxDelay = maxDelay;
   }

   public void setFactory(@Nullable Function<Builder, RetryPolicy> factory) {
      this.factory = factory;
   }
}
