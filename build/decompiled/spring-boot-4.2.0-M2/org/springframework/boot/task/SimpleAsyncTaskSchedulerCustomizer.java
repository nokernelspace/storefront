package org.springframework.boot.task;

import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@FunctionalInterface
public interface SimpleAsyncTaskSchedulerCustomizer {
   void customize(SimpleAsyncTaskScheduler taskScheduler);
}
