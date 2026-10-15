package org.springframework.boot.task;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@FunctionalInterface
public interface ThreadPoolTaskSchedulerCustomizer {
   void customize(ThreadPoolTaskScheduler taskScheduler);
}
