package org.springframework.util.backoff;

import java.util.StringJoiner;
import org.springframework.util.Assert;

public class ExponentialBackOff implements BackOff {
   public static final long DEFAULT_INITIAL_INTERVAL = 2000L;
   public static final long DEFAULT_JITTER = 0L;
   public static final double DEFAULT_MULTIPLIER = 1.5;
   public static final long DEFAULT_MAX_INTERVAL = 30000L;
   public static final long DEFAULT_MAX_ELAPSED_TIME = Long.MAX_VALUE;
   public static final long DEFAULT_MAX_ATTEMPTS = Long.MAX_VALUE;
   private long initialInterval = 2000L;
   private long jitter = 0L;
   private double multiplier = 1.5;
   private long maxInterval = 30000L;
   private long maxElapsedTime = Long.MAX_VALUE;
   private long maxAttempts = Long.MAX_VALUE;

   public ExponentialBackOff() {
   }

   public ExponentialBackOff(long initialInterval, double multiplier) {
      this.checkMultiplier(multiplier);
      this.initialInterval = initialInterval;
      this.multiplier = multiplier;
   }

   public void setInitialInterval(long initialInterval) {
      this.initialInterval = initialInterval;
   }

   public long getInitialInterval() {
      return this.initialInterval;
   }

   public void setJitter(long jitter) {
      Assert.isTrue(jitter >= 0L, () -> "Invalid jitter '" + jitter + "': must be >= 0.");
      this.jitter = jitter;
   }

   public long getJitter() {
      return this.jitter;
   }

   public void setMultiplier(double multiplier) {
      this.checkMultiplier(multiplier);
      this.multiplier = multiplier;
   }

   private void checkMultiplier(double multiplier) {
      Assert.isTrue(
         multiplier >= 1.0,
         () -> "Invalid multiplier '" + multiplier + "': Should be greater than or equal to 1. A multiplier of 1 is equivalent to a fixed interval."
      );
   }

   public double getMultiplier() {
      return this.multiplier;
   }

   public void setMaxInterval(long maxInterval) {
      this.maxInterval = maxInterval;
   }

   public long getMaxInterval() {
      return this.maxInterval;
   }

   public void setMaxElapsedTime(long maxElapsedTime) {
      this.maxElapsedTime = maxElapsedTime;
   }

   public long getMaxElapsedTime() {
      return this.maxElapsedTime;
   }

   public void setMaxAttempts(long maxAttempts) {
      this.maxAttempts = maxAttempts;
   }

   public long getMaxAttempts() {
      return this.maxAttempts;
   }

   @Override
   public BackOffExecution start() {
      return new ExponentialBackOff.ExponentialBackOffExecution();
   }

   @Override
   public String toString() {
      return new StringJoiner(", ", "ExponentialBackOff[", "]")
         .add("initialInterval=" + this.initialInterval)
         .add("jitter=" + this.jitter)
         .add("multiplier=" + this.multiplier)
         .add("maxInterval=" + this.maxInterval)
         .add("maxElapsedTime=" + this.maxElapsedTime)
         .add("maxAttempts=" + this.maxAttempts)
         .toString();
   }

   private class ExponentialBackOffExecution implements BackOffExecution {
      private long currentInterval = -1L;
      private long currentElapsedTime = 0L;
      private int attempts = 0;

      @Override
      public long nextBackOff() {
         if (this.currentElapsedTime < ExponentialBackOff.this.getMaxElapsedTime() && this.attempts < ExponentialBackOff.this.getMaxAttempts()) {
            long nextInterval = this.computeNextInterval();
            this.currentElapsedTime += nextInterval;
            this.attempts++;
            return nextInterval;
         } else {
            return -1L;
         }
      }

      private long computeNextInterval() {
         long maxInterval = ExponentialBackOff.this.getMaxInterval();
         long nextInterval;
         if (this.currentInterval < 0L) {
            nextInterval = ExponentialBackOff.this.getInitialInterval();
         } else if (this.currentInterval >= maxInterval) {
            nextInterval = maxInterval;
         } else {
            nextInterval = Math.min((long)(this.currentInterval * ExponentialBackOff.this.getMultiplier()), maxInterval);
         }

         this.currentInterval = nextInterval;
         return Math.min(this.applyJitter(nextInterval), maxInterval);
      }

      private long applyJitter(long interval) {
         long jitter = ExponentialBackOff.this.getJitter();
         if (jitter > 0L) {
            long initialInterval = ExponentialBackOff.this.getInitialInterval();
            long applicableJitter = (long)(jitter * (initialInterval > 0L ? (double)interval / initialInterval : 1.0));
            long min = Math.max(interval - applicableJitter, initialInterval);
            long max = Math.min(interval + applicableJitter, ExponentialBackOff.this.getMaxInterval());
            return min + (long)(Math.random() * (max - min));
         } else {
            return interval;
         }
      }

      @Override
      public String toString() {
         String currentIntervalDescription = this.currentInterval < 0L ? "n/a" : this.currentInterval + "ms";
         return new StringJoiner(", ", "ExponentialBackOffExecution[", "]")
            .add("currentInterval=" + currentIntervalDescription)
            .add("multiplier=" + ExponentialBackOff.this.getMultiplier())
            .add("attempts=" + this.attempts)
            .toString();
      }
   }
}
