package org.springframework.scheduling.config;

import java.time.Duration;
import org.springframework.util.Assert;

public class DelayedTask extends Task {
   private final Duration initialDelay;

   public DelayedTask(Runnable runnable, Duration initialDelay) {
      super(runnable);
      Assert.notNull(initialDelay, "InitialDelay must not be null");
      this.initialDelay = initialDelay;
   }

   DelayedTask(DelayedTask task) {
      super(task.getRunnable());
      Assert.notNull(task, "DelayedTask must not be null");
      this.initialDelay = task.getInitialDelayDuration();
   }

   public Duration getInitialDelayDuration() {
      return this.initialDelay;
   }
}
