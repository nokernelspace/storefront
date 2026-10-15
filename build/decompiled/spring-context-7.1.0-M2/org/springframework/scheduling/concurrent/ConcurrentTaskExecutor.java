package org.springframework.scheduling.concurrent;

import jakarta.enterprise.concurrent.ManagedExecutors;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.util.ClassUtils;

public class ConcurrentTaskExecutor implements AsyncTaskExecutor, SchedulingTaskExecutor {
   private static final Executor STUB_EXECUTOR = task -> {
      throw new IllegalStateException("Executor not configured");
   };
   private static @Nullable Class<?> managedExecutorServiceClass;
   private Executor concurrentExecutor = STUB_EXECUTOR;
   private TaskExecutorAdapter adaptedExecutor = new TaskExecutorAdapter(STUB_EXECUTOR);
   private @Nullable TaskDecorator taskDecorator;

   @Deprecated(since = "6.1")
   public ConcurrentTaskExecutor() {
      this.concurrentExecutor = Executors.newSingleThreadExecutor();
      this.adaptedExecutor = new TaskExecutorAdapter(this.concurrentExecutor);
   }

   public ConcurrentTaskExecutor(@Nullable Executor executor) {
      if (executor != null) {
         this.setConcurrentExecutor(executor);
      }
   }

   public final void setConcurrentExecutor(Executor executor) {
      this.concurrentExecutor = executor;
      this.adaptedExecutor = this.getAdaptedExecutor(this.concurrentExecutor);
   }

   public final Executor getConcurrentExecutor() {
      return this.concurrentExecutor;
   }

   public final void setTaskDecorator(TaskDecorator taskDecorator) {
      this.taskDecorator = taskDecorator;
      this.adaptedExecutor.setTaskDecorator(taskDecorator);
   }

   public void execute(Runnable task) {
      this.adaptedExecutor.execute(task);
   }

   @Deprecated(since = "5.3.16")
   public void execute(Runnable task, long startTimeout) {
      this.adaptedExecutor.execute(task, startTimeout);
   }

   public Future<?> submit(Runnable task) {
      return this.adaptedExecutor.submit(task);
   }

   public <T> Future<T> submit(Callable<T> task) {
      return this.adaptedExecutor.submit(task);
   }

   private TaskExecutorAdapter getAdaptedExecutor(Executor originalExecutor) {
      TaskExecutorAdapter adapter = managedExecutorServiceClass != null && managedExecutorServiceClass.isInstance(originalExecutor)
         ? new ConcurrentTaskExecutor.ManagedTaskExecutorAdapter(originalExecutor)
         : new TaskExecutorAdapter(originalExecutor);
      if (this.taskDecorator != null) {
         adapter.setTaskDecorator(this.taskDecorator);
      }

      return adapter;
   }

   Runnable decorateTaskIfNecessary(Runnable task) {
      return this.taskDecorator != null ? this.taskDecorator.decorate(task) : task;
   }

   static {
      try {
         managedExecutorServiceClass = ClassUtils.forName(
            "jakarta.enterprise.concurrent.ManagedExecutorService", ConcurrentTaskScheduler.class.getClassLoader()
         );
      } catch (ClassNotFoundException ex) {
         managedExecutorServiceClass = null;
      }
   }

   protected static class ManagedTaskBuilder {
      public static Runnable buildManagedTask(Runnable task, String identityName) {
         Map<String, String> properties;
         if (task instanceof SchedulingAwareRunnable schedulingAwareRunnable) {
            properties = new HashMap<>(4);
            properties.put("jakarta.enterprise.concurrent.LONGRUNNING_HINT", Boolean.toString(schedulingAwareRunnable.isLongLived()));
         } else {
            properties = new HashMap<>(2);
         }

         properties.put("jakarta.enterprise.concurrent.IDENTITY_NAME", identityName);
         return ManagedExecutors.managedTask(task, properties, null);
      }

      public static <T> Callable<T> buildManagedTask(Callable<T> task, String identityName) {
         Map<String, String> properties = new HashMap<>(2);
         properties.put("jakarta.enterprise.concurrent.IDENTITY_NAME", identityName);
         return ManagedExecutors.managedTask(task, properties, null);
      }
   }

   private static class ManagedTaskExecutorAdapter extends TaskExecutorAdapter {
      public ManagedTaskExecutorAdapter(Executor concurrentExecutor) {
         super(concurrentExecutor);
      }

      public void execute(Runnable task) {
         super.execute(ConcurrentTaskExecutor.ManagedTaskBuilder.buildManagedTask(task, task.toString()));
      }

      public Future<?> submit(Runnable task) {
         return super.submit(ConcurrentTaskExecutor.ManagedTaskBuilder.buildManagedTask(task, task.toString()));
      }

      public <T> Future<T> submit(Callable<T> task) {
         return super.submit(ConcurrentTaskExecutor.ManagedTaskBuilder.buildManagedTask(task, task.toString()));
      }
   }
}
