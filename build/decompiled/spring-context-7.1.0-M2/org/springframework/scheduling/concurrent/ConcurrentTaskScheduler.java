package org.springframework.scheduling.concurrent;

import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ErrorHandler;

public class ConcurrentTaskScheduler extends ConcurrentTaskExecutor implements TaskScheduler {
   private static final TimeUnit NANO = TimeUnit.NANOSECONDS;
   private static @Nullable Class<?> managedScheduledExecutorServiceClass;
   private @Nullable ScheduledExecutorService scheduledExecutor;
   private boolean enterpriseConcurrentScheduler = false;
   private @Nullable ErrorHandler errorHandler;
   private Clock clock = Clock.systemDefaultZone();

   @Deprecated(since = "6.1")
   public ConcurrentTaskScheduler() {
      this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
      this.enterpriseConcurrentScheduler = false;
   }

   public ConcurrentTaskScheduler(@Nullable ScheduledExecutorService scheduledExecutor) {
      super(scheduledExecutor);
      if (scheduledExecutor != null) {
         this.initScheduledExecutor(scheduledExecutor);
      }
   }

   public ConcurrentTaskScheduler(Executor concurrentExecutor, ScheduledExecutorService scheduledExecutor) {
      super(concurrentExecutor);
      this.initScheduledExecutor(scheduledExecutor);
   }

   private void initScheduledExecutor(ScheduledExecutorService scheduledExecutor) {
      this.scheduledExecutor = scheduledExecutor;
      this.enterpriseConcurrentScheduler = managedScheduledExecutorServiceClass != null && managedScheduledExecutorServiceClass.isInstance(scheduledExecutor);
   }

   public void setScheduledExecutor(ScheduledExecutorService scheduledExecutor) {
      this.initScheduledExecutor(scheduledExecutor);
   }

   private ScheduledExecutorService getScheduledExecutor() {
      if (this.scheduledExecutor == null) {
         throw new IllegalStateException("No ScheduledExecutor is configured");
      } else {
         return this.scheduledExecutor;
      }
   }

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

   @Override
   public void execute(Runnable task) {
      super.execute(TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, false));
   }

   @Override
   public Future<?> submit(Runnable task) {
      return super.submit(TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, false));
   }

   @Override
   public <T> Future<T> submit(Callable<T> task) {
      return super.submit(new DelegatingErrorHandlingCallable<>(task, this.errorHandler));
   }

   @Override
   public @Nullable ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      ScheduledExecutorService scheduleExecutorToUse = this.getScheduledExecutor();

      try {
         if (this.enterpriseConcurrentScheduler) {
            return new ConcurrentTaskScheduler.EnterpriseConcurrentTriggerScheduler().schedule(this.decorateTask(task, true), trigger);
         }

         ErrorHandler errorHandler = this.errorHandler != null ? this.errorHandler : TaskUtils.getDefaultErrorHandler(true);
         return new ReschedulingRunnable(this.decorateTaskIfNecessary(task), trigger, this.clock, scheduleExecutorToUse, errorHandler).schedule();
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(scheduleExecutorToUse, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      ScheduledExecutorService scheduleExecutorToUse = this.getScheduledExecutor();
      Duration delay = Duration.between(this.clock.instant(), startTime);

      try {
         return scheduleExecutorToUse.schedule(this.decorateTask(task, false), NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(scheduleExecutorToUse, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
      ScheduledExecutorService scheduleExecutorToUse = this.getScheduledExecutor();
      Duration initialDelay = Duration.between(this.clock.instant(), startTime);

      try {
         return scheduleExecutorToUse.scheduleAtFixedRate(this.decorateTask(task, true), NANO.convert(initialDelay), NANO.convert(period), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(scheduleExecutorToUse, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      ScheduledExecutorService scheduleExecutorToUse = this.getScheduledExecutor();

      try {
         return scheduleExecutorToUse.scheduleAtFixedRate(this.decorateTask(task, true), 0L, NANO.convert(period), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(scheduleExecutorToUse, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
      ScheduledExecutorService scheduleExecutorToUse = this.getScheduledExecutor();
      Duration initialDelay = Duration.between(this.clock.instant(), startTime);

      try {
         return scheduleExecutorToUse.scheduleWithFixedDelay(this.decorateTask(task, true), NANO.convert(initialDelay), NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(scheduleExecutorToUse, task, ex);
      }
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      ScheduledExecutorService scheduleExecutorToUse = this.getScheduledExecutor();

      try {
         return scheduleExecutorToUse.scheduleWithFixedDelay(this.decorateTask(task, true), 0L, NANO.convert(delay), NANO);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(scheduleExecutorToUse, task, ex);
      }
   }

   private Runnable decorateTask(Runnable task, boolean isRepeatingTask) {
      Runnable result = TaskUtils.decorateTaskWithErrorHandler(task, this.errorHandler, isRepeatingTask);
      result = this.decorateTaskIfNecessary(result);
      if (this.enterpriseConcurrentScheduler) {
         result = ConcurrentTaskExecutor.ManagedTaskBuilder.buildManagedTask(result, task.toString());
      }

      return result;
   }

   static {
      try {
         managedScheduledExecutorServiceClass = ClassUtils.forName(
            "jakarta.enterprise.concurrent.ManagedScheduledExecutorService", ConcurrentTaskScheduler.class.getClassLoader()
         );
      } catch (ClassNotFoundException ex) {
         managedScheduledExecutorServiceClass = null;
      }
   }

   private class EnterpriseConcurrentTriggerScheduler {
      public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
         ManagedScheduledExecutorService executor = (ManagedScheduledExecutorService)ConcurrentTaskScheduler.this.getScheduledExecutor();
         return executor.schedule(task, new ConcurrentTaskScheduler.EnterpriseConcurrentTriggerScheduler.TriggerAdapter(trigger));
      }

      private static class TriggerAdapter implements jakarta.enterprise.concurrent.Trigger {
         private final Trigger adaptee;

         public TriggerAdapter(Trigger adaptee) {
            this.adaptee = adaptee;
         }

         public @Nullable Date getNextRunTime(@Nullable LastExecution le, Date taskScheduledTime) {
            Instant instant = this.adaptee
               .nextExecution(new ConcurrentTaskScheduler.EnterpriseConcurrentTriggerScheduler.TriggerAdapter.LastExecutionAdapter(le));
            return instant != null ? Date.from(instant) : null;
         }

         public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
            return false;
         }

         private static class LastExecutionAdapter implements TriggerContext {
            private final @Nullable LastExecution le;

            public LastExecutionAdapter(@Nullable LastExecution le) {
               this.le = le;
            }

            @Override
            public @Nullable Instant lastScheduledExecution() {
               return this.le != null ? toInstant(this.le.getScheduledStart()) : null;
            }

            @Override
            public @Nullable Instant lastActualExecution() {
               return this.le != null ? toInstant(this.le.getRunStart()) : null;
            }

            @Override
            public @Nullable Instant lastCompletion() {
               return this.le != null ? toInstant(this.le.getRunEnd()) : null;
            }

            private static @Nullable Instant toInstant(@Nullable Date date) {
               return date != null ? date.toInstant() : null;
            }
         }
      }
   }
}
