package org.springframework.scheduling.config;

import java.time.Duration;

public class FixedRateTask extends IntervalTask {
   @Deprecated(since = "6.0")
   public FixedRateTask(Runnable runnable, long interval, long initialDelay) {
      super(runnable, interval, initialDelay);
   }

   public FixedRateTask(Runnable runnable, Duration interval, Duration initialDelay) {
      super(runnable, interval, initialDelay);
   }

   FixedRateTask(IntervalTask task) {
      super(task);
   }
}
