package org.springframework.scheduling.config;

import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ScheduledTaskRegistrar implements ScheduledTaskHolder, InitializingBean, DisposableBean {
   public static final String CRON_DISABLED = "-";
   private @Nullable TaskScheduler taskScheduler;
   private @Nullable ScheduledExecutorService localExecutor;
   private @Nullable ObservationRegistry observationRegistry;
   private @Nullable List<TriggerTask> triggerTasks;
   private @Nullable List<CronTask> cronTasks;
   private @Nullable List<IntervalTask> fixedRateTasks;
   private @Nullable List<IntervalTask> fixedDelayTasks;
   private @Nullable List<DelayedTask> oneTimeTasks;
   private final Map<Task, ScheduledTask> unresolvedTasks = new HashMap<>(16);
   private final Set<ScheduledTask> scheduledTasks = new LinkedHashSet<>(16);

   public void setTaskScheduler(TaskScheduler taskScheduler) {
      Assert.notNull(taskScheduler, "TaskScheduler must not be null");
      this.taskScheduler = taskScheduler;
   }

   public void setScheduler(@Nullable Object scheduler) {
      if (scheduler == null) {
         this.taskScheduler = null;
      } else if (scheduler instanceof TaskScheduler ts) {
         this.taskScheduler = ts;
      } else {
         if (!(scheduler instanceof ScheduledExecutorService ses)) {
            throw new IllegalArgumentException("Unsupported scheduler type: " + scheduler.getClass());
         }

         this.taskScheduler = new ConcurrentTaskScheduler(ses);
      }
   }

   public @Nullable TaskScheduler getScheduler() {
      return this.taskScheduler;
   }

   public void setObservationRegistry(@Nullable ObservationRegistry observationRegistry) {
      this.observationRegistry = observationRegistry;
   }

   public @Nullable ObservationRegistry getObservationRegistry() {
      return this.observationRegistry;
   }

   public void setTriggerTasks(Map<Runnable, Trigger> triggerTasks) {
      this.triggerTasks = new ArrayList<>();
      triggerTasks.forEach((task, trigger) -> this.addTriggerTask(new TriggerTask(task, trigger)));
   }

   public void setTriggerTasksList(List<TriggerTask> triggerTasks) {
      this.triggerTasks = triggerTasks;
   }

   public List<TriggerTask> getTriggerTaskList() {
      return this.triggerTasks != null ? Collections.unmodifiableList(this.triggerTasks) : Collections.emptyList();
   }

   public void setCronTasks(Map<Runnable, String> cronTasks) {
      this.cronTasks = new ArrayList<>();
      cronTasks.forEach(this::addCronTask);
   }

   public void setCronTasksList(List<CronTask> cronTasks) {
      this.cronTasks = cronTasks;
   }

   public List<CronTask> getCronTaskList() {
      return this.cronTasks != null ? Collections.unmodifiableList(this.cronTasks) : Collections.emptyList();
   }

   public void setFixedRateTasks(Map<Runnable, Long> fixedRateTasks) {
      this.fixedRateTasks = new ArrayList<>();
      fixedRateTasks.forEach((task, interval) -> this.addFixedRateTask(task, Duration.ofMillis(interval)));
   }

   public void setFixedRateTasksList(List<IntervalTask> fixedRateTasks) {
      this.fixedRateTasks = fixedRateTasks;
   }

   public List<IntervalTask> getFixedRateTaskList() {
      return this.fixedRateTasks != null ? Collections.unmodifiableList(this.fixedRateTasks) : Collections.emptyList();
   }

   public void setFixedDelayTasks(Map<Runnable, Long> fixedDelayTasks) {
      this.fixedDelayTasks = new ArrayList<>();
      fixedDelayTasks.forEach((task, delay) -> this.addFixedDelayTask(task, Duration.ofMillis(delay)));
   }

   public void setFixedDelayTasksList(List<IntervalTask> fixedDelayTasks) {
      this.fixedDelayTasks = fixedDelayTasks;
   }

   public List<IntervalTask> getFixedDelayTaskList() {
      return this.fixedDelayTasks != null ? Collections.unmodifiableList(this.fixedDelayTasks) : Collections.emptyList();
   }

   public void addTriggerTask(Runnable task, Trigger trigger) {
      this.addTriggerTask(new TriggerTask(task, trigger));
   }

   public void addTriggerTask(TriggerTask task) {
      if (this.triggerTasks == null) {
         this.triggerTasks = new ArrayList<>();
      }

      this.triggerTasks.add(task);
   }

   public void addCronTask(Runnable task, String expression) {
      if (!"-".equals(expression)) {
         this.addCronTask(new CronTask(task, expression));
      }
   }

   public void addCronTask(Runnable task, String expression, ZoneId zoneId) {
      if (!"-".equals(expression)) {
         this.addCronTask(new CronTask(task, new CronTrigger(expression, zoneId)));
      }
   }

   public void addCronTask(CronTask task) {
      if (this.cronTasks == null) {
         this.cronTasks = new ArrayList<>();
      }

      this.cronTasks.add(task);
   }

   @Deprecated(since = "6.0")
   public void addFixedRateTask(Runnable task, long interval) {
      this.addFixedRateTask(new IntervalTask(task, interval));
   }

   public void addFixedRateTask(Runnable task, Duration interval) {
      this.addFixedRateTask(new IntervalTask(task, interval));
   }

   public void addFixedRateTask(IntervalTask task) {
      if (this.fixedRateTasks == null) {
         this.fixedRateTasks = new ArrayList<>();
      }

      this.fixedRateTasks.add(task);
   }

   @Deprecated(since = "6.0")
   public void addFixedDelayTask(Runnable task, long interval) {
      this.addFixedDelayTask(new IntervalTask(task, interval));
   }

   public void addFixedDelayTask(Runnable task, Duration interval) {
      this.addFixedDelayTask(new IntervalTask(task, interval));
   }

   public void addFixedDelayTask(IntervalTask task) {
      if (this.fixedDelayTasks == null) {
         this.fixedDelayTasks = new ArrayList<>();
      }

      this.fixedDelayTasks.add(task);
   }

   public void addOneTimeTask(Runnable task, Duration initialDelay) {
      this.addOneTimeTask(new OneTimeTask(task, initialDelay));
   }

   public void addOneTimeTask(DelayedTask task) {
      if (this.oneTimeTasks == null) {
         this.oneTimeTasks = new ArrayList<>();
      }

      this.oneTimeTasks.add(task);
   }

   public boolean hasTasks() {
      return !CollectionUtils.isEmpty(this.triggerTasks)
         || !CollectionUtils.isEmpty(this.cronTasks)
         || !CollectionUtils.isEmpty(this.fixedRateTasks)
         || !CollectionUtils.isEmpty(this.fixedDelayTasks)
         || !CollectionUtils.isEmpty(this.oneTimeTasks);
   }

   public void afterPropertiesSet() {
      this.scheduleTasks();
   }

   protected void scheduleTasks() {
      if (this.taskScheduler == null) {
         this.localExecutor = Executors.newSingleThreadScheduledExecutor();
         this.taskScheduler = new ConcurrentTaskScheduler(this.localExecutor);
      }

      if (this.triggerTasks != null) {
         for (TriggerTask task : this.triggerTasks) {
            this.addScheduledTask(this.scheduleTriggerTask(task));
         }
      }

      if (this.cronTasks != null) {
         for (CronTask task : this.cronTasks) {
            this.addScheduledTask(this.scheduleCronTask(task));
         }
      }

      if (this.fixedRateTasks != null) {
         for (IntervalTask task : this.fixedRateTasks) {
            if (task instanceof FixedRateTask fixedRateTask) {
               this.addScheduledTask(this.scheduleFixedRateTask(fixedRateTask));
            } else {
               this.addScheduledTask(this.scheduleFixedRateTask(new FixedRateTask(task)));
            }
         }
      }

      if (this.fixedDelayTasks != null) {
         for (IntervalTask task : this.fixedDelayTasks) {
            if (task instanceof FixedDelayTask fixedDelayTask) {
               this.addScheduledTask(this.scheduleFixedDelayTask(fixedDelayTask));
            } else {
               this.addScheduledTask(this.scheduleFixedDelayTask(new FixedDelayTask(task)));
            }
         }
      }

      if (this.oneTimeTasks != null) {
         for (DelayedTask task : this.oneTimeTasks) {
            if (task instanceof OneTimeTask oneTimeTask) {
               this.addScheduledTask(this.scheduleOneTimeTask(oneTimeTask));
            } else {
               this.addScheduledTask(this.scheduleOneTimeTask(new OneTimeTask(task)));
            }
         }
      }
   }

   private void addScheduledTask(@Nullable ScheduledTask task) {
      if (task != null) {
         this.scheduledTasks.add(task);
      }
   }

   public @Nullable ScheduledTask scheduleTriggerTask(TriggerTask task) {
      ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
      boolean newTask = false;
      if (scheduledTask == null) {
         scheduledTask = new ScheduledTask(task);
         newTask = true;
      }

      if (this.taskScheduler != null) {
         scheduledTask.future = this.taskScheduler.schedule(task.getRunnable(), task.getTrigger());
      } else {
         this.addTriggerTask(task);
         this.unresolvedTasks.put(task, scheduledTask);
      }

      return newTask ? scheduledTask : null;
   }

   public @Nullable ScheduledTask scheduleCronTask(CronTask task) {
      ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
      boolean newTask = false;
      if (scheduledTask == null) {
         scheduledTask = new ScheduledTask(task);
         newTask = true;
      }

      if (this.taskScheduler != null) {
         scheduledTask.future = this.taskScheduler.schedule(task.getRunnable(), task.getTrigger());
      } else {
         this.addCronTask(task);
         this.unresolvedTasks.put(task, scheduledTask);
      }

      return newTask ? scheduledTask : null;
   }

   public @Nullable ScheduledTask scheduleFixedRateTask(FixedRateTask task) {
      ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
      boolean newTask = false;
      if (scheduledTask == null) {
         scheduledTask = new ScheduledTask(task);
         newTask = true;
      }

      if (this.taskScheduler != null) {
         Duration initialDelay = task.getInitialDelayDuration();
         if (initialDelay.toNanos() > 0L) {
            Instant startTime = this.taskScheduler.getClock().instant().plus(initialDelay);
            scheduledTask.future = this.taskScheduler.scheduleAtFixedRate(task.getRunnable(), startTime, task.getIntervalDuration());
         } else {
            scheduledTask.future = this.taskScheduler.scheduleAtFixedRate(task.getRunnable(), task.getIntervalDuration());
         }
      } else {
         this.addFixedRateTask(task);
         this.unresolvedTasks.put(task, scheduledTask);
      }

      return newTask ? scheduledTask : null;
   }

   public @Nullable ScheduledTask scheduleFixedDelayTask(FixedDelayTask task) {
      ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
      boolean newTask = false;
      if (scheduledTask == null) {
         scheduledTask = new ScheduledTask(task);
         newTask = true;
      }

      if (this.taskScheduler != null) {
         Duration initialDelay = task.getInitialDelayDuration();
         if (!initialDelay.isNegative()) {
            Instant startTime = this.taskScheduler.getClock().instant().plus(task.getInitialDelayDuration());
            scheduledTask.future = this.taskScheduler.scheduleWithFixedDelay(task.getRunnable(), startTime, task.getIntervalDuration());
         } else {
            scheduledTask.future = this.taskScheduler.scheduleWithFixedDelay(task.getRunnable(), task.getIntervalDuration());
         }
      } else {
         this.addFixedDelayTask(task);
         this.unresolvedTasks.put(task, scheduledTask);
      }

      return newTask ? scheduledTask : null;
   }

   public @Nullable ScheduledTask scheduleOneTimeTask(OneTimeTask task) {
      ScheduledTask scheduledTask = this.unresolvedTasks.remove(task);
      boolean newTask = false;
      if (scheduledTask == null) {
         scheduledTask = new ScheduledTask(task);
         newTask = true;
      }

      if (this.taskScheduler != null) {
         Instant startTime = this.taskScheduler.getClock().instant().plus(task.getInitialDelayDuration());
         scheduledTask.future = this.taskScheduler.schedule(task.getRunnable(), startTime);
      } else {
         this.addOneTimeTask(task);
         this.unresolvedTasks.put(task, scheduledTask);
      }

      return newTask ? scheduledTask : null;
   }

   @Override
   public Set<ScheduledTask> getScheduledTasks() {
      return Collections.unmodifiableSet(this.scheduledTasks);
   }

   public void destroy() {
      for (ScheduledTask task : this.scheduledTasks) {
         task.cancel(false);
      }

      if (this.localExecutor != null) {
         this.localExecutor.shutdownNow();
      }
   }
}
