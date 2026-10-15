package org.springframework.core.task;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrencyThrottleSupport;
import org.springframework.util.CustomizableThreadCreator;

public class SimpleAsyncTaskExecutor extends CustomizableThreadCreator implements AsyncTaskExecutor, Serializable, AutoCloseable {
   public static final int UNBOUNDED_CONCURRENCY = -1;
   public static final int NO_CONCURRENCY = 0;
   private final SimpleAsyncTaskExecutor.ConcurrencyThrottleAdapter concurrencyThrottle = new SimpleAsyncTaskExecutor.ConcurrencyThrottleAdapter();
   private @Nullable VirtualThreadDelegate virtualThreadDelegate;
   private @Nullable ThreadFactory threadFactory;
   private @Nullable TaskDecorator taskDecorator;
   private long taskTerminationTimeout;
   private @Nullable Set<Thread> activeThreads;
   private boolean cancelRemainingTasksOnClose = false;
   private boolean rejectTasksWhenLimitReached = false;
   private final AtomicBoolean closed = new AtomicBoolean();
   private boolean cancelled = false;

   public SimpleAsyncTaskExecutor() {
   }

   public SimpleAsyncTaskExecutor(String threadNamePrefix) {
      super(threadNamePrefix);
   }

   public SimpleAsyncTaskExecutor(ThreadFactory threadFactory) {
      this.threadFactory = threadFactory;
   }

   public void setVirtualThreads(boolean virtual) {
      this.virtualThreadDelegate = virtual ? new VirtualThreadDelegate() : null;
   }

   public void setThreadFactory(@Nullable ThreadFactory threadFactory) {
      this.threadFactory = threadFactory;
   }

   public final @Nullable ThreadFactory getThreadFactory() {
      return this.threadFactory;
   }

   public void setTaskDecorator(TaskDecorator taskDecorator) {
      this.taskDecorator = taskDecorator;
   }

   public void setTaskTerminationTimeout(long timeout) {
      Assert.isTrue(timeout >= 0L, "Timeout value must be >=0");
      this.taskTerminationTimeout = timeout;
      this.trackActiveThreadsIfNecessary();
   }

   public void setCancelRemainingTasksOnClose(boolean cancelRemainingTasksOnClose) {
      this.cancelRemainingTasksOnClose = cancelRemainingTasksOnClose;
      this.trackActiveThreadsIfNecessary();
   }

   public void setRejectTasksWhenLimitReached(boolean rejectTasksWhenLimitReached) {
      this.rejectTasksWhenLimitReached = rejectTasksWhenLimitReached;
   }

   public void setConcurrencyLimit(int concurrencyLimit) {
      this.concurrencyThrottle.setConcurrencyLimit(concurrencyLimit);
   }

   public final int getConcurrencyLimit() {
      return this.concurrencyThrottle.getConcurrencyLimit();
   }

   public final boolean isThrottleActive() {
      return this.concurrencyThrottle.isThrottleActive();
   }

   public boolean isActive() {
      return !this.closed.get();
   }

   private void trackActiveThreadsIfNecessary() {
      this.activeThreads = this.taskTerminationTimeout <= 0L && !this.cancelRemainingTasksOnClose ? null : ConcurrentHashMap.newKeySet();
   }

   @Override
   public void execute(Runnable task) {
      this.execute(task, Long.MAX_VALUE);
   }

   @Deprecated(since = "5.3.16")
   @Override
   public void execute(Runnable task, long startTimeout) {
      Assert.notNull(task, "Runnable must not be null");
      if (!this.isActive()) {
         throw new TaskRejectedException(this.getClass().getSimpleName() + " is not active");
      }

      Runnable taskToUse = this.taskDecorator != null ? this.taskDecorator.decorate(task) : task;
      Future<?> future = task instanceof Future<?> f ? f : null;
      if (this.isThrottleActive() && startTimeout > 0L) {
         this.concurrencyThrottle.beforeAccess();

         try {
            this.doExecute(new SimpleAsyncTaskExecutor.TaskTrackingRunnable(taskToUse, future));
         } catch (Throwable ex) {
            this.concurrencyThrottle.afterAccess();
            throw new TaskRejectedException("Failed to start execution thread for task: " + task, ex);
         }
      } else if (this.activeThreads != null) {
         this.doExecute(new SimpleAsyncTaskExecutor.TaskTrackingRunnable(taskToUse, future));
      } else {
         this.doExecute(taskToUse);
      }
   }

   @Override
   public Future<?> submit(Runnable task) {
      FutureTask<Object> future = new FutureTask<>(task, null);
      this.execute(future, Long.MAX_VALUE);
      return future;
   }

   @Override
   public <T> Future<T> submit(Callable<T> task) {
      FutureTask<T> future = new FutureTask<>(task);
      this.execute(future, Long.MAX_VALUE);
      return future;
   }

   protected void doExecute(Runnable task) {
      this.newThread(task).start();
   }

   protected Thread newThread(Runnable task) {
      if (this.virtualThreadDelegate != null) {
         return this.virtualThreadDelegate.newVirtualThread(this.nextThreadName(), task);
      } else {
         return this.threadFactory != null ? this.threadFactory.newThread(task) : this.createThread(task);
      }
   }

   @Override
   public void close() {
      if (this.closed.compareAndSet(false, true)) {
         Set<Thread> threads = this.activeThreads;
         if (threads != null) {
            if (this.cancelRemainingTasksOnClose) {
               synchronized (threads) {
                  this.cancelled = true;
               }

               threads.forEach(Thread::interrupt);
            }

            if (this.taskTerminationTimeout > 0L) {
               synchronized (threads) {
                  try {
                     if (!threads.isEmpty()) {
                        threads.wait(this.taskTerminationTimeout);
                     }
                  } catch (InterruptedException ex) {
                     Thread.currentThread().interrupt();
                  }

                  this.cancelled = true;
               }

               if (!this.cancelRemainingTasksOnClose) {
                  threads.forEach(Thread::interrupt);
               }
            }
         }
      }
   }

   private void checkCancelled(@Nullable Future<?> future) {
      if (this.cancelled) {
         if (future != null) {
            future.cancel(false);
         }

         throw new CancellationException(this.getClass().getSimpleName() + " has cancelled all remaining tasks");
      }
   }

   private class ConcurrencyThrottleAdapter extends ConcurrencyThrottleSupport {
      @Override
      protected void beforeAccess() {
         super.beforeAccess();
      }

      @Override
      protected void onLimitReached() {
         if (SimpleAsyncTaskExecutor.this.rejectTasksWhenLimitReached) {
            this.onAccessRejected("Concurrency limit reached: " + this.getConcurrencyLimit());
         }

         super.onLimitReached();
      }

      @Override
      protected void onAccessRejected(String msg) {
         throw new TaskRejectedException(msg);
      }

      @Override
      protected void afterAccess() {
         super.afterAccess();
      }
   }

   private class TaskTrackingRunnable implements Runnable {
      private final Runnable task;
      private final @Nullable Future<?> future;

      public TaskTrackingRunnable(Runnable task, @Nullable Future<?> future) {
         Assert.notNull(task, "Task must not be null");
         this.task = task;
         this.future = future;
      }

      @Override
      public void run() {
         Set<Thread> threads = SimpleAsyncTaskExecutor.this.activeThreads;
         Thread thread = null;
         if (threads != null) {
            thread = Thread.currentThread();
            synchronized (threads) {
               SimpleAsyncTaskExecutor.this.checkCancelled(this.future);
               threads.add(thread);
            }
         }

         try {
            this.task.run();
         } finally {
            if (threads != null) {
               threads.remove(thread);
               if (SimpleAsyncTaskExecutor.this.closed.get()) {
                  synchronized (threads) {
                     if (threads.isEmpty()) {
                        threads.notify();
                     }
                  }
               }
            }

            SimpleAsyncTaskExecutor.this.concurrencyThrottle.afterAccess();
         }
      }
   }
}
