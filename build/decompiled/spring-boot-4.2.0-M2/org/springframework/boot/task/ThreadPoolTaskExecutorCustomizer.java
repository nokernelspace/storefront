package org.springframework.boot.task;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@FunctionalInterface
public interface ThreadPoolTaskExecutorCustomizer {
   void customize(ThreadPoolTaskExecutor taskExecutor);
}
