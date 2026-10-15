package org.springframework.scheduling.config;

import java.time.Duration;

public class OneTimeTask extends DelayedTask {
   public OneTimeTask(Runnable runnable, Duration initialDelay) {
      super(runnable, initialDelay);
   }

   OneTimeTask(DelayedTask task) {
      super(task);
   }
}
