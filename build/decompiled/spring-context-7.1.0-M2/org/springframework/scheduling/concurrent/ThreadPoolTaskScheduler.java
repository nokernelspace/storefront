package org.springframework.scheduling.concurrent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

public class ThreadPoolTaskScheduler extends ExecutorConfigurationSupport implements AsyncTaskExecutor, SchedulingTaskExecutor, TaskScheduler {
   private static final TimeUnit NANO = TimeUnit.NANOSECONDS;
   private volatile int poolSize = 1;
   private volatile boolean removeOnCancelPolicy;
   private volatile boolean continueExistingPeriodicTasksAfterShutdownPolicy;
   private volatile boolean executeExistingDelayedTasksAfterShutdownPolicy = true;
   private @Nullable TaskDecorator taskDecorator;
   private volatile @Nullable ErrorHandler errorHandler;
   private Clock clock = Clock.systemDefaultZone();
   private @Nullable ScheduledExecutorService scheduledExecutor;

   public void setPoolSize(int poolSize) {
      Assert.isTrue(poolSize > 0, "'poolSize' must be 1 or higher");
      if (this.scheduledExecutor instanceof ScheduledThreadPoolExecutor threadPoolExecutor) {
         threadPoolExecutor.setCorePoolSize(poolSize);
      }

      this.poolSize = poolSize;
   }

   public void setRemoveOnCancelPolicy(boolean flag) {
      if (this.scheduledExecutor instanceof ScheduledThreadPoolExecutor threadPoolExecutor) {
         threadPoolExecutor.setRemoveOnCancelPolicy(flag);
      }

      this.removeOnCancelPolicy = flag;
   }

   public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean flag) {
      if (this.scheduledExecutor instanceof ScheduledThreadPoolExecutor threadPoolExecutor) {
         threadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(flag);
      }

      this.continueExistingPeriodicTasksAfterShutdownPolicy = flag;
   }

   public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean flag) {
      if (this.scheduledExecutor instanceof ScheduledThreadPoolExecutor threadPoolExecutor) {
         threadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(flag);
      }

      this.executeExistingDelayedTasksAfterShutdownPolicy = flag;
   }

   public void setTaskDecorator(TaskDecorator taskDecorator) {
      this.taskDecorator = taskDecorator;
   }

   public void setErrorHandler(ErrorHandler errorHandler) {
      this.errorHandler = errorHandler;
   }

   public void setClock(Clock clock) {
      Assert.notNull(clock, "Clock must not be null");
      this.clock = clock;
   }

   @Override
   public Clock getClock() {
      return this.clock;
   }

   @Override
   protected ExecutorService initializeExecutor(ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
      this.scheduledExecutor = this.createExecutor(this.poolSize, threadFactory, rejectedExecutionHandler);
      if (this.scheduledExecutor instanceof ScheduledThreadPoolExecutor threadPoolExecutor) {
         if (this.removeOnCancelPolicy) {
            threadPoolExecutor.setRemoveOnCancelPolicy(true);
         }

         if (this.continueExistingPeriodicTasksAfterShutdownPolicy) {
            threadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
         }

         if (!this.executeExistingDelayedTasksAfterShutdownPolicy) {
            threadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
         }
      }

      return this.scheduledExecutor;
   }

   protected ScheduledExecutorService createExecutor(int poolSize, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
      return new ScheduledThreadPoolExecutor(poolSize, threadFactory, rejectedExecutionHandler) {
         @Override
         protected void beforeExecute(Thread thread, Runnable task) {
            ThreadPoolTaskScheduler.this.beforeExecute(thread, task);
         }

         @Override
         protected void afterExecute(Runnable task, Throwable ex) {
            ThreadPoolTaskScheduler.this.afterExecute(task, ex);
         }

         @Override
         protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable, RunnableScheduledFuture<V> task) {
            return ThreadPoolTaskScheduler.this.decorateTaskIfNecessary(task);
         }

         @Override
         protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
            return ThreadPoolTaskScheduler.this.decorateTaskIfNecessary(task);
         }
      };
   }

   public ScheduledExecutorService getScheduledExecutor() throws IllegalStateException {
      Assert.state(this.scheduledExecutor != null, "ThreadPoolTaskScheduler not initialized");
      return this.scheduledExecutor;
   }

   public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() throws IllegalStateException {
      Assert.state(this.scheduledExecutor instanceof ScheduledThreadPoolExecutor, "No ScheduledThreadPoolExecutor available");
      return (ScheduledThreadPoolExecutor)this.scheduledExecutor;
   }

   public int getPoolSize() {
      return this.scheduledExecutor == null ? this.poolSize : this.getScheduledThreadPoolExecutor().getPoolSize();
   }

   public int getActiveCount() {
      return this.scheduledExecutor == null ? 0 : this.getScheduledThreadPoolExecutor().getActiveCount();
   }

   @Deprecated(since = "5.3.9")
   public boolean isRemoveOnCancelPolicy() {
      return this.scheduledExecutor == null ? this.removeOnCancelPolicy : this.getScheduledThreadPoolExecutor().getRemoveOnCancelPolicy();
   }

   public void execute(Runnable task) {
      Executor executor = this.getScheduledExecutor();

      try {
         executor.execute(this.errorHandlingTask(task, false));
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   public Future<?> submit(Runnable task) {
      ExecutorService executor = this.getScheduledExecutor();

      try {
         return executor.submit(this.errorHandlingTask(task, false));
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   public <T> Future<T> submit(Callable<T> task) {
      ExecutorService executor = this.getScheduledExecutor();

      try {
         return executor.submit(new DelegatingErrorHandlingCallable<>(task, this.errorHandler));
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   public @Nullable ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      ScheduledExecutorService executor = this.getScheduledExecutor();

      try {
         ErrorHandler errorHandler = this.errorHandler;
         if (errorHandler == null) {
            errorHandler = TaskUtils.getDefaultErrorHandler(true);
         }

         return new ReschedulingRunnable(task, trigger, this.clock, executor, errorHandler).schedule();
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      ScheduledExecutorService executor = this.getScheduledExecutor();
      Duration delay = Duration.between(this.clock.instant(), startTime);

      try {
         return executor.schedule(this.errorHandlingTask(task, false), NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
      ScheduledExecutorService executor = this.getScheduledExecutor();
      Duration initialDelay = Duration.between(this.clock.instant(), startTime);

      try {
         return executor.scheduleAtFixedRate(this.errorHandlingTask(task, true), NANO.convert(initialDelay), NANO.convert(period), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      ScheduledExecutorService executor = this.getScheduledExecutor();

      try {
         return executor.scheduleAtFixedRate(this.errorHandlingTask(task, true), 0L, NANO.convert(period), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
      ScheduledExecutorService executor = this.getScheduledExecutor();
      Duration initialDelay = Duration.between(this.clock.instant(), startTime);

      try {
         return executor.scheduleWithFixedDelay(this.errorHandlingTask(task, true), NANO.convert(initialDelay), NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      ScheduledExecutorService executor = this.getScheduledExecutor();

      try {
         return executor.scheduleWithFixedDelay(this.errorHandlingTask(task, true), 0L, NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   private <V> RunnableScheduledFuture<V> decorateTaskIfNecessary(RunnableScheduledFuture<V> future) {
      return this.taskDecorator != null ? new ThreadPoolTaskScheduler.DelegatingRunnableScheduledFuture<>(future, this.taskDecorator) : future;
   }

   private Runnable errorHandlingTask(Runnable task, boolean isRepeatingTask) {
      return TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, isRepeatingTask);
   }

   private static class DelegatingRunnableScheduledFuture<V> implements RunnableScheduledFuture<V> {
      private final RunnableScheduledFuture<V> future;
      private final Runnable decoratedRunnable;

      public DelegatingRunnableScheduledFuture(RunnableScheduledFuture<V> future, TaskDecorator taskDecorator) {
         this.future = future;
         this.decoratedRunnable = taskDecorator.decorate(this.future);
      }

      @Override
      public void run() {
         this.decoratedRunnable.run();
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
         return this.future.cancel(mayInterruptIfRunning);
      }

      @Override
      public boolean isCancelled() {
         return this.future.isCancelled();
      }

      @Override
      public boolean isDone() {
         return this.future.isDone();
      }

      @Override
      public V get() throws InterruptedException, ExecutionException {
         return this.future.get();
      }

      @Override
      public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
         return this.future.get(timeout, unit);
      }

      @Override
      public boolean isPeriodic() {
         return this.future.isPeriodic();
      }

      @Override
      public long getDelay(TimeUnit unit) {
         return this.future.getDelay(unit);
      }

      public int compareTo(Delayed o) {
         return this.future.compareTo(o);
      }
   }
}
