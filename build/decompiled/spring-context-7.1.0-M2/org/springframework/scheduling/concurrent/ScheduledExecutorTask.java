package org.springframework.scheduling.concurrent;

import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class ScheduledExecutorTask {
   private @Nullable Runnable runnable;
   private long delay = 0L;
   private long period = -1L;
   private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
   private boolean fixedRate = false;

   public ScheduledExecutorTask() {
   }

   public ScheduledExecutorTask(Runnable executorTask) {
      this.runnable = executorTask;
   }

   public ScheduledExecutorTask(Runnable executorTask, long delay) {
      this.runnable = executorTask;
      this.delay = delay;
   }

   public ScheduledExecutorTask(Runnable executorTask, long delay, long period, boolean fixedRate) {
      this.runnable = executorTask;
      this.delay = delay;
      this.period = period;
      this.fixedRate = fixedRate;
   }

   public void setRunnable(Runnable executorTask) {
      this.runnable = executorTask;
   }

   public Runnable getRunnable() {
      Assert.state(this.runnable != null, "No Runnable set");
      return this.runnable;
   }

   public void setDelay(long delay) {
      this.delay = delay;
   }

   public long getDelay() {
      return this.delay;
   }

   public void setPeriod(long period) {
      this.period = period;
   }

   public long getPeriod() {
      return this.period;
   }

   public boolean isOneTimeTask() {
      return this.period <= 0L;
   }

   public void setTimeUnit(@Nullable TimeUnit timeUnit) {
      this.timeUnit = timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS;
   }

   public TimeUnit getTimeUnit() {
      return this.timeUnit;
   }

   public void setFixedRate(boolean fixedRate) {
      this.fixedRate = fixedRate;
   }

   public boolean isFixedRate() {
      return this.fixedRate;
   }
}
