package org.springframework.scheduling.config;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.util.Assert;

public class Task {
   private final Runnable runnable;
   private TaskExecutionOutcome lastExecutionOutcome;

   public Task(Runnable runnable) {
      Assert.notNull(runnable, "Runnable must not be null");
      this.runnable = new Task.OutcomeTrackingRunnable(runnable);
      this.lastExecutionOutcome = TaskExecutionOutcome.create();
   }

   public Runnable getRunnable() {
      return this.runnable;
   }

   public TaskExecutionOutcome getLastExecutionOutcome() {
      return this.lastExecutionOutcome;
   }

   @Override
   public String toString() {
      return this.runnable.toString();
   }

   private class OutcomeTrackingRunnable implements SchedulingAwareRunnable {
      private final Runnable runnable;

      public OutcomeTrackingRunnable(Runnable runnable) {
         this.runnable = runnable;
      }

      @Override
      public void run() {
         try {
            Task.this.lastExecutionOutcome = Task.this.lastExecutionOutcome.start(Instant.now());
            this.runnable.run();
            Task.this.lastExecutionOutcome = Task.this.lastExecutionOutcome.success();
         } catch (Throwable exc) {
            Task.this.lastExecutionOutcome = Task.this.lastExecutionOutcome.failure(exc);
            throw exc;
         }
      }

      @Override
      public boolean isLongLived() {
         return this.runnable instanceof SchedulingAwareRunnable sar ? sar.isLongLived() : SchedulingAwareRunnable.super.isLongLived();
      }

      @Override
      public @Nullable String getQualifier() {
         return this.runnable instanceof SchedulingAwareRunnable sar ? sar.getQualifier() : SchedulingAwareRunnable.super.getQualifier();
      }

      @Override
      public String toString() {
         return this.runnable.toString();
      }
   }
}
