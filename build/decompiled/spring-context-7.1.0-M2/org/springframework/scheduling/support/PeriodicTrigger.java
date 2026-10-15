package org.springframework.scheduling.support;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class PeriodicTrigger implements Trigger {
   private final Duration period;
   private final @Nullable ChronoUnit chronoUnit;
   private volatile @Nullable Duration initialDelay;
   private volatile boolean fixedRate;

   @Deprecated(since = "6.0")
   public PeriodicTrigger(long period) {
      this(period, null);
   }

   @Deprecated(since = "6.0")
   public PeriodicTrigger(long period, @Nullable TimeUnit timeUnit) {
      this(toDuration(period, timeUnit), timeUnit);
   }

   private static Duration toDuration(long amount, @Nullable TimeUnit timeUnit) {
      return timeUnit != null ? Duration.of(amount, timeUnit.toChronoUnit()) : Duration.ofMillis(amount);
   }

   public PeriodicTrigger(Duration period) {
      this(period, null);
   }

   private PeriodicTrigger(Duration period, @Nullable TimeUnit timeUnit) {
      Assert.notNull(period, "Period must not be null");
      Assert.isTrue(!period.isNegative(), "Period must not be negative");
      this.period = period;
      if (timeUnit != null) {
         this.chronoUnit = timeUnit.toChronoUnit();
      } else {
         this.chronoUnit = null;
      }
   }

   @Deprecated(since = "6.0")
   public long getPeriod() {
      return this.chronoUnit != null ? this.period.get(this.chronoUnit) : this.period.toMillis();
   }

   public Duration getPeriodDuration() {
      return this.period;
   }

   @Deprecated(since = "6.0")
   public TimeUnit getTimeUnit() {
      return this.chronoUnit != null ? TimeUnit.of(this.chronoUnit) : TimeUnit.MILLISECONDS;
   }

   @Deprecated(since = "6.0")
   public void setInitialDelay(long initialDelay) {
      if (this.chronoUnit != null) {
         this.initialDelay = Duration.of(initialDelay, this.chronoUnit);
      } else {
         this.initialDelay = Duration.ofMillis(initialDelay);
      }
   }

   public void setInitialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
   }

   @Deprecated(since = "6.0")
   public long getInitialDelay() {
      Duration initialDelay = this.initialDelay;
      if (initialDelay != null) {
         return this.chronoUnit != null ? initialDelay.get(this.chronoUnit) : initialDelay.toMillis();
      } else {
         return 0L;
      }
   }

   public @Nullable Duration getInitialDelayDuration() {
      return this.initialDelay;
   }

   public void setFixedRate(boolean fixedRate) {
      this.fixedRate = fixedRate;
   }

   public boolean isFixedRate() {
      return this.fixedRate;
   }

   @Override
   public Instant nextExecution(TriggerContext triggerContext) {
      Instant lastExecution = triggerContext.lastScheduledExecution();
      Instant lastCompletion = triggerContext.lastCompletion();
      if (lastExecution == null || lastCompletion == null) {
         Instant instant = triggerContext.getClock().instant();
         Duration initialDelay = this.initialDelay;
         return initialDelay == null ? instant : instant.plus(initialDelay);
      } else {
         return this.fixedRate ? lastExecution.plus(this.period) : lastCompletion.plus(this.period);
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof PeriodicTrigger that
            && this.fixedRate == that.fixedRate
            && this.period.equals(that.period)
            && ObjectUtils.nullSafeEquals(this.initialDelay, that.initialDelay);
   }

   @Override
   public int hashCode() {
      return this.period.hashCode();
   }
}
