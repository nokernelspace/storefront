package org.springframework.scheduling.config;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public record TaskExecutionOutcome(@Nullable Instant executionTime, TaskExecutionOutcome.Status status, @Nullable Throwable throwable) {
   TaskExecutionOutcome start(Instant executionTime) {
      return new TaskExecutionOutcome(executionTime, TaskExecutionOutcome.Status.STARTED, null);
   }

   TaskExecutionOutcome success() {
      Assert.state(this.executionTime != null, "Task has not been started yet");
      return new TaskExecutionOutcome(this.executionTime, TaskExecutionOutcome.Status.SUCCESS, null);
   }

   TaskExecutionOutcome failure(Throwable throwable) {
      Assert.state(this.executionTime != null, "Task has not been started yet");
      return new TaskExecutionOutcome(this.executionTime, TaskExecutionOutcome.Status.ERROR, throwable);
   }

   static TaskExecutionOutcome create() {
      return new TaskExecutionOutcome(null, TaskExecutionOutcome.Status.NONE, null);
   }

   public enum Status {
      NONE,
      STARTED,
      SUCCESS,
      ERROR;
   }
}
