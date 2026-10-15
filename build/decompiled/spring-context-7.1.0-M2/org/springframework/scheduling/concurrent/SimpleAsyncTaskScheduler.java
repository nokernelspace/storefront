package org.springframework.scheduling.concurrent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

public class SimpleAsyncTaskScheduler
   extends SimpleAsyncTaskExecutor
   implements TaskScheduler,
   ApplicationContextAware,
   SmartLifecycle,
   ApplicationListener<ContextClosedEvent> {
   public static final int DEFAULT_PHASE = 1073741823;
   private static final TimeUnit NANO = TimeUnit.NANOSECONDS;
   private final ScheduledExecutorService triggerExecutor = this.createScheduledExecutor();
   private final ExecutorLifecycleDelegate triggerLifecycle = new ExecutorLifecycleDelegate(this.triggerExecutor);
   private final ScheduledExecutorService fixedDelayExecutor = this.createFixedDelayExecutor();
   private final ExecutorLifecycleDelegate fixedDelayLifecycle = new ExecutorLifecycleDelegate(this.fixedDelayExecutor);
   private @Nullable ErrorHandler errorHandler;
   private Clock clock = Clock.systemDefaultZone();
   private int phase = 1073741823;
   private @Nullable Executor targetTaskExecutor;
   private @Nullable ApplicationContext applicationContext;

   public void setErrorHandler(ErrorHandler errorHandler) {
      Assert.notNull(errorHandler, "ErrorHandler must not be null");
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

   public void setPhase(int phase) {
      this.phase = phase;
   }

   @Override
   public int getPhase() {
      return this.phase;
   }

   public void setTargetTaskExecutor(Executor targetTaskExecutor) {
      this.targetTaskExecutor = targetTaskExecutor == this ? null : targetTaskExecutor;
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   private ScheduledExecutorService createScheduledExecutor() {
      return new ScheduledThreadPoolExecutor(1, x$0 -> this.newThread(x$0)) {
         @Override
         protected void beforeExecute(Thread thread, Runnable task) {
            SimpleAsyncTaskScheduler.this.triggerLifecycle.beforeExecute(thread);
         }

         @Override
         protected void afterExecute(Runnable task, Throwable ex) {
            SimpleAsyncTaskScheduler.this.triggerLifecycle.afterExecute();
         }
      };
   }

   private ScheduledExecutorService createFixedDelayExecutor() {
      return new ScheduledThreadPoolExecutor(1, x$0 -> this.newThread(x$0)) {
         @Override
         protected void beforeExecute(Thread thread, Runnable task) {
            SimpleAsyncTaskScheduler.this.fixedDelayLifecycle.beforeExecute(thread);
         }

         @Override
         protected void afterExecute(Runnable task, Throwable ex) {
            SimpleAsyncTaskScheduler.this.fixedDelayLifecycle.afterExecute();
         }
      };
   }

   protected void doExecute(Runnable task) {
      if (this.targetTaskExecutor != null) {
         this.targetTaskExecutor.execute(task);
      } else {
         super.doExecute(task);
      }
   }

   private Runnable taskOnSchedulerThread(Runnable task) {
      return new DelegatingErrorHandlingRunnable(task, this.errorHandler != null ? this.errorHandler : TaskUtils.getDefaultErrorHandler(true));
   }

   private Runnable scheduledTask(Runnable task) {
      return () -> this.execute(new DelegatingErrorHandlingRunnable(task, this::shutdownAwareErrorHandler));
   }

   private void shutdownAwareErrorHandler(Throwable ex) {
      if (this.errorHandler != null) {
         this.errorHandler.handleError(ex);
      } else if (this.triggerExecutor.isShutdown()) {
         LogFactory.getLog(this.getClass()).debug("Ignoring scheduled task exception after shutdown", ex);
      } else {
         TaskUtils.getDefaultErrorHandler(true).handleError(ex);
      }
   }

   public void execute(Runnable task) {
      super.execute(TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, false));
   }

   public Future<?> submit(Runnable task) {
      return super.submit(TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, false));
   }

   public <T> Future<T> submit(Callable<T> task) {
      return super.submit(new DelegatingErrorHandlingCallable<>(task, this.errorHandler));
   }

   @Override
   public @Nullable ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      try {
         Runnable delegate = this.scheduledTask(task);
         ErrorHandler errorHandler = this.errorHandler != null ? this.errorHandler : TaskUtils.getDefaultErrorHandler(true);
         return new ReschedulingRunnable(delegate, trigger, this.clock, this.triggerExecutor, errorHandler).schedule();
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.triggerExecutor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      Duration delay = Duration.between(this.clock.instant(), startTime);

      try {
         return this.triggerExecutor.schedule(this.scheduledTask(task), NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.triggerExecutor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
      Duration initialDelay = Duration.between(this.clock.instant(), startTime);

      try {
         return this.triggerExecutor.scheduleAtFixedRate(this.scheduledTask(task), NANO.convert(initialDelay), NANO.convert(period), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.triggerExecutor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      try {
         return this.triggerExecutor.scheduleAtFixedRate(this.scheduledTask(task), 0L, NANO.convert(period), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.triggerExecutor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
      Duration initialDelay = Duration.between(this.clock.instant(), startTime);

      try {
         return this.fixedDelayExecutor.scheduleWithFixedDelay(this.taskOnSchedulerThread(task), NANO.convert(initialDelay), NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.fixedDelayExecutor, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      try {
         return this.fixedDelayExecutor.scheduleWithFixedDelay(this.taskOnSchedulerThread(task), 0L, NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(this.fixedDelayExecutor, task, ex);
      }
   }

   @Override
   public void start() {
      this.triggerLifecycle.start();
      this.fixedDelayLifecycle.start();
   }

   @Override
   public void stop() {
      this.triggerLifecycle.stop();
      this.fixedDelayLifecycle.stop();
   }

   @Override
   public void stop(Runnable callback) {
      this.triggerLifecycle.stop();
      this.fixedDelayLifecycle.stop(callback);
   }

   @Override
   public boolean isRunning() {
      return this.triggerLifecycle.isRunning() || this.fixedDelayLifecycle.isRunning();
   }

   public void onApplicationEvent(ContextClosedEvent event) {
      if (event.getApplicationContext() == this.applicationContext) {
         this.triggerExecutor.shutdown();
         this.fixedDelayExecutor.shutdown();
      }
   }

   public void close() {
      for (Runnable remainingTask : this.triggerExecutor.shutdownNow()) {
         if (remainingTask instanceof Future<?> future) {
            future.cancel(true);
         }
      }

      for (Runnable remainingTask : this.fixedDelayExecutor.shutdownNow()) {
         if (remainingTask instanceof Future<?> future) {
            future.cancel(true);
         }
      }

      super.close();
   }
}
