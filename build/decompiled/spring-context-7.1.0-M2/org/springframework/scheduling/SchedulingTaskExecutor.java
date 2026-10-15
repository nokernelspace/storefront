package org.springframework.scheduling;

import org.springframework.core.task.AsyncTaskExecutor;

public interface SchedulingTaskExecutor extends AsyncTaskExecutor {
   default boolean prefersShortLivedTasks() {
      return true;
   }
}
