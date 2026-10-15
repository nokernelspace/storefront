package org.springframework.scheduling.concurrent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

class ReschedulingRunnable extends DelegatingErrorHandlingRunnable implements ScheduledFuture<Object> {
   private final Trigger trigger;
   private final SimpleTriggerContext triggerContext;
   private final ScheduledExecutorService executor;
   private @Nullable ScheduledFuture<?> currentFuture;
   private @Nullable Instant scheduledExecutionTime;
   private final Object triggerContextMonitor = new Object();

   public ReschedulingRunnable(Runnable delegate, Trigger trigger, Clock clock, ScheduledExecutorService executor, ErrorHandler errorHandler) {
      super(delegate, errorHandler);
      this.trigger = trigger;
      this.triggerContext = new SimpleTriggerContext(clock);
      this.executor = executor;
   }

   public @Nullable ScheduledFuture<?> schedule() {
      synchronized (this.triggerContextMonitor) {
         this.scheduledExecutionTime = this.trigger.nextExecution(this.triggerContext);
         if (this.scheduledExecutionTime == null) {
            return null;
         }

         Duration delay = Duration.between(this.triggerContext.getClock().instant(), this.scheduledExecutionTime);
         this.currentFuture = this.executor.schedule(this, delay.toNanos(), TimeUnit.NANOSECONDS);
         return this;
      }
   }

   private ScheduledFuture<?> obtainCurrentFuture() {
      Assert.state(this.currentFuture != null, "No scheduled future");
      return this.currentFuture;
   }

   @Override
   public void run() {
      Instant actualExecutionTime = this.triggerContext.getClock().instant();
      super.run();
      Instant completionTime = this.triggerContext.getClock().instant();
      synchronized (this.triggerContextMonitor) {
         Assert.state(this.scheduledExecutionTime != null, "No scheduled execution");
         this.triggerContext.update(this.scheduledExecutionTime, actualExecutionTime, completionTime);
         if (!this.obtainCurrentFuture().isCancelled()) {
            this.schedule();
         }
      }
   }

   @Override
   public boolean cancel(boolean mayInterruptIfRunning) {
      synchronized (this.triggerContextMonitor) {
         return this.obtainCurrentFuture().cancel(mayInterruptIfRunning);
      }
   }

   @Override
   public boolean isCancelled() {
      synchronized (this.triggerContextMonitor) {
         return this.obtainCurrentFuture().isCancelled();
      }
   }

   @Override
   public boolean isDone() {
      synchronized (this.triggerContextMonitor) {
         return this.obtainCurrentFuture().isDone();
      }
   }

   @Override
   public Object get() throws InterruptedException, ExecutionException {
      ScheduledFuture<?> curr;
      synchronized (this.triggerContextMonitor) {
         curr = this.obtainCurrentFuture();
      }

      return curr.get();
   }

   @Override
   public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      ScheduledFuture<?> curr;
      synchronized (this.triggerContextMonitor) {
         curr = this.obtainCurrentFuture();
      }

      return curr.get(timeout, unit);
   }

   @Override
   public long getDelay(TimeUnit unit) {
      ScheduledFuture<?> curr;
      synchronized (this.triggerContextMonitor) {
         curr = this.obtainCurrentFuture();
      }

      return curr.getDelay(unit);
   }

   public int compareTo(Delayed other) {
      if (this == other) {
         return 0;
      }

      long diff = this.getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
      return diff == 0L ? 0 : (diff < 0L ? -1 : 1);
   }
}
