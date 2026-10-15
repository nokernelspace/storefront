package org.springframework.scheduling.config;

import java.time.Duration;

public class FixedDelayTask extends IntervalTask {
   @Deprecated(since = "6.0")
   public FixedDelayTask(Runnable runnable, long interval, long initialDelay) {
      super(runnable, interval, initialDelay);
   }

   public FixedDelayTask(Runnable runnable, Duration interval, Duration initialDelay) {
      super(runnable, interval, initialDelay);
   }

   FixedDelayTask(IntervalTask task) {
      super(task);
   }
}
