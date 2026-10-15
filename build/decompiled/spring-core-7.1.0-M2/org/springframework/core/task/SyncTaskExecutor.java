package org.springframework.core.task;

import java.io.Serializable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrencyThrottleSupport;

public class SyncTaskExecutor extends ConcurrencyThrottleSupport implements TaskExecutor, Serializable {
   private boolean rejectTasksWhenLimitReached = false;

   public void setRejectTasksWhenLimitReached(boolean rejectTasksWhenLimitReached) {
      this.rejectTasksWhenLimitReached = rejectTasksWhenLimitReached;
   }

   @Override
   public void execute(Runnable task) {
      Assert.notNull(task, "Task must not be null");
      if (this.isThrottleActive()) {
         this.beforeAccess();

         try {
            task.run();
         } finally {
            this.afterAccess();
         }
      } else {
         task.run();
      }
   }

   public <V, E extends Exception> V execute(TaskCallback<V, E> task) throws E {
      Assert.notNull(task, "Task must not be null");
      if (this.isThrottleActive()) {
         this.beforeAccess();

         try {
            return task.call();
         } finally {
            this.afterAccess();
         }
      } else {
         return task.call();
      }
   }

   @Override
   protected void onLimitReached() {
      if (this.rejectTasksWhenLimitReached) {
         this.onAccessRejected("Concurrency limit reached: " + this.getConcurrencyLimit());
      }

      super.onLimitReached();
   }

   @Override
   protected void onAccessRejected(String msg) {
      throw new TaskRejectedException(msg);
   }
}
