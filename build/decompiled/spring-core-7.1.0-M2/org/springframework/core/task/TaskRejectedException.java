package org.springframework.core.task;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class TaskRejectedException extends RejectedExecutionException {
   public TaskRejectedException(String msg) {
      super(msg);
   }

   public TaskRejectedException(String msg, Throwable cause) {
      super(msg, cause);
   }

   public TaskRejectedException(Executor executor, Object task, RejectedExecutionException cause) {
      super(executorDescription(executor) + " did not accept task: " + task, cause);
   }

   private static String executorDescription(Executor executor) {
      if (executor instanceof ExecutorService executorService) {
         try {
            return "ExecutorService in " + (executorService.isShutdown() ? "shutdown" : "active") + " state";
         } catch (Exception var3) {
         }
      }

      return executor.toString();
   }
}
