package org.springframework.boot.task;

import org.springframework.core.task.SimpleAsyncTaskExecutor;

@FunctionalInterface
public interface SimpleAsyncTaskExecutorCustomizer {
   void customize(SimpleAsyncTaskExecutor taskExecutor);
}
