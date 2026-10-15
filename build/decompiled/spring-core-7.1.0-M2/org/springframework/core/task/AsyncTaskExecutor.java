package org.springframework.core.task;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.springframework.util.concurrent.FutureUtils;

public interface AsyncTaskExecutor extends TaskExecutor {
   @Deprecated(since = "5.3.16")
   long TIMEOUT_IMMEDIATE = 0L;
   @Deprecated(since = "5.3.16")
   long TIMEOUT_INDEFINITE = Long.MAX_VALUE;

   @Deprecated(since = "5.3.16")
   default void execute(Runnable task, long startTimeout) {
      this.execute(task);
   }

   default Future<?> submit(Runnable task) {
      FutureTask<Object> future = new FutureTask<>(task, null);
      this.execute(future);
      return future;
   }

   default <T> Future<T> submit(Callable<T> task) {
      FutureTask<T> future = new FutureTask<>(task);
      this.execute(future, Long.MAX_VALUE);
      return future;
   }

   default CompletableFuture<Void> submitCompletable(Runnable task) {
      return CompletableFuture.runAsync(task, this);
   }

   default <T> CompletableFuture<T> submitCompletable(Callable<T> task) {
      return FutureUtils.callAsync(task, this);
   }
}
