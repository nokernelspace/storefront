package org.springframework.scheduling.config;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

public final class ScheduledTask {
   private final Task task;
   volatile @Nullable ScheduledFuture<?> future;

   ScheduledTask(Task task) {
      this.task = task;
   }

   public Task getTask() {
      return this.task;
   }

   public void cancel() {
      this.cancel(true);
   }

   public void cancel(boolean mayInterruptIfRunning) {
      ScheduledFuture<?> future = this.future;
      if (future != null) {
         future.cancel(mayInterruptIfRunning);
      }
   }

   public @Nullable Instant nextExecution() {
      ScheduledFuture<?> future = this.future;
      if (future != null && !future.isCancelled()) {
         long delay = future.getDelay(TimeUnit.MILLISECONDS);
         if (delay > 0L) {
            return Instant.now().plusMillis(delay);
         }
      }

      return null;
   }

   @Override
   public String toString() {
      return this.task.toString();
   }
}
