package org.springframework.core.task.support;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.Assert;

public class TaskExecutorAdapter implements AsyncTaskExecutor {
   private final Executor concurrentExecutor;
   private @Nullable TaskDecorator taskDecorator;

   public TaskExecutorAdapter(Executor concurrentExecutor) {
      Assert.notNull(concurrentExecutor, "Executor must not be null");
      this.concurrentExecutor = concurrentExecutor;
   }

   public final void setTaskDecorator(TaskDecorator taskDecorator) {
      this.taskDecorator = taskDecorator;
   }

   @Override
   public void execute(Runnable task) {
      try {
         this.doExecute(this.concurrentExecutor, this.taskDecorator, task);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.concurrentExecutor, task, ex);
      }
   }

   @Override
   public Future<?> submit(Runnable task) {
      try {
         if (this.taskDecorator == null && this.concurrentExecutor instanceof ExecutorService executorService) {
            return executorService.submit(task);
         }

         FutureTask<Object> future = new FutureTask<>(task, null);
         this.doExecute(this.concurrentExecutor, this.taskDecorator, future);
         return future;
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.concurrentExecutor, task, ex);
      }
   }

   @Override
   public <T> Future<T> submit(Callable<T> task) {
      try {
         if (this.taskDecorator == null && this.concurrentExecutor instanceof ExecutorService executorService) {
            return executorService.submit(task);
         }

         FutureTask<T> future = new FutureTask<>(task);
         this.doExecute(this.concurrentExecutor, this.taskDecorator, future);
         return future;
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.concurrentExecutor, task, ex);
      }
   }

   protected void doExecute(Executor concurrentExecutor, @Nullable TaskDecorator taskDecorator, Runnable runnable) throws RejectedExecutionException {
      concurrentExecutor.execute(taskDecorator != null ? taskDecorator.decorate(runnable) : runnable);
   }
}
