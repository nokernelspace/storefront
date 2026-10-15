package org.springframework.util.backoff;

public class FixedBackOff implements BackOff {
   public static final long DEFAULT_INTERVAL = 5000L;
   public static final long UNLIMITED_ATTEMPTS = Long.MAX_VALUE;
   private long interval = 5000L;
   private long maxAttempts = Long.MAX_VALUE;

   public FixedBackOff() {
   }

   public FixedBackOff(long interval) {
      this.interval = interval;
   }

   public FixedBackOff(long interval, long maxAttempts) {
      this.interval = interval;
      this.maxAttempts = maxAttempts;
   }

   public void setInterval(long interval) {
      this.interval = interval;
   }

   public long getInterval() {
      return this.interval;
   }

   public void setMaxAttempts(long maxAttempts) {
      this.maxAttempts = maxAttempts;
   }

   public long getMaxAttempts() {
      return this.maxAttempts;
   }

   @Override
   public BackOffExecution start() {
      return new FixedBackOff.FixedBackOffExecution();
   }

   @Override
   public String toString() {
      String attemptValue = this.maxAttempts == Long.MAX_VALUE ? "unlimited" : String.valueOf(this.maxAttempts);
      return "FixedBackOff[interval=" + this.interval + ", maxAttempts=" + attemptValue + "]";
   }

   private class FixedBackOffExecution implements BackOffExecution {
      private long currentAttempts = 0L;

      @Override
      public long nextBackOff() {
         this.currentAttempts++;
         return this.currentAttempts <= FixedBackOff.this.getMaxAttempts() ? FixedBackOff.this.getInterval() : -1L;
      }

      @Override
      public String toString() {
         String attemptValue = FixedBackOff.this.maxAttempts == Long.MAX_VALUE ? "unlimited" : String.valueOf(FixedBackOff.this.maxAttempts);
         return "FixedBackOffExecution[interval="
            + FixedBackOff.this.interval
            + ", currentAttempts="
            + this.currentAttempts
            + ", maxAttempts="
            + attemptValue
            + "]";
      }
   }
}
