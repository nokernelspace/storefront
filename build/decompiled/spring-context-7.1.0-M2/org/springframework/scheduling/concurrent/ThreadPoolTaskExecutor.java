package org.springframework.scheduling.concurrent;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.SchedulingTaskExecutor;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ConcurrentReferenceHashMap.ReferenceType;

public class ThreadPoolTaskExecutor extends ExecutorConfigurationSupport implements AsyncTaskExecutor, SchedulingTaskExecutor {
   private final Object poolSizeMonitor = new Object();
   private int corePoolSize = 1;
   private int maxPoolSize = Integer.MAX_VALUE;
   private int keepAliveSeconds = 60;
   private int queueCapacity = Integer.MAX_VALUE;
   private boolean allowCoreThreadTimeOut = false;
   private boolean prestartAllCoreThreads = false;
   private boolean strictEarlyShutdown = false;
   private @Nullable TaskDecorator taskDecorator;
   private @Nullable ThreadPoolExecutor threadPoolExecutor;
   private final Map<Runnable, Object> decoratedTaskMap = new ConcurrentReferenceHashMap(16, ReferenceType.WEAK);

   public void setCorePoolSize(int corePoolSize) {
      synchronized (this.poolSizeMonitor) {
         if (this.threadPoolExecutor != null) {
            this.threadPoolExecutor.setCorePoolSize(corePoolSize);
         }

         this.corePoolSize = corePoolSize;
      }
   }

   public int getCorePoolSize() {
      synchronized (this.poolSizeMonitor) {
         return this.corePoolSize;
      }
   }

   public void setMaxPoolSize(int maxPoolSize) {
      synchronized (this.poolSizeMonitor) {
         if (this.threadPoolExecutor != null) {
            this.threadPoolExecutor.setMaximumPoolSize(maxPoolSize);
         }

         this.maxPoolSize = maxPoolSize;
      }
   }

   public int getMaxPoolSize() {
      synchronized (this.poolSizeMonitor) {
         return this.maxPoolSize;
      }
   }

   public void setKeepAliveSeconds(int keepAliveSeconds) {
      synchronized (this.poolSizeMonitor) {
         if (this.threadPoolExecutor != null) {
            this.threadPoolExecutor.setKeepAliveTime(keepAliveSeconds, TimeUnit.SECONDS);
         }

         this.keepAliveSeconds = keepAliveSeconds;
      }
   }

   public int getKeepAliveSeconds() {
      synchronized (this.poolSizeMonitor) {
         return this.keepAliveSeconds;
      }
   }

   public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
   }

   public int getQueueCapacity() {
      return this.queueCapacity;
   }

   public void setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
      this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
   }

   public void setPrestartAllCoreThreads(boolean prestartAllCoreThreads) {
      this.prestartAllCoreThreads = prestartAllCoreThreads;
   }

   public void setStrictEarlyShutdown(boolean strictEarlyShutdown) {
      this.strictEarlyShutdown = strictEarlyShutdown;
   }

   public void setTaskDecorator(TaskDecorator taskDecorator) {
      this.taskDecorator = taskDecorator;
   }

   @Override
   protected ExecutorService initializeExecutor(ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
      BlockingQueue<Runnable> queue = this.createQueue(this.queueCapacity);
      ThreadPoolExecutor executor = new ThreadPoolExecutor(
         this.corePoolSize, this.maxPoolSize, this.keepAliveSeconds, TimeUnit.SECONDS, queue, threadFactory, rejectedExecutionHandler
      ) {
         @Override
         public void execute(Runnable command) {
            Runnable decorated = command;
            if (ThreadPoolTaskExecutor.this.taskDecorator != null) {
               decorated = ThreadPoolTaskExecutor.this.taskDecorator.decorate(command);
               if (decorated != command) {
                  ThreadPoolTaskExecutor.this.decoratedTaskMap.put(decorated, command);
               }
            }

            super.execute(decorated);
         }

         @Override
         protected void beforeExecute(Thread thread, Runnable task) {
            ThreadPoolTaskExecutor.this.beforeExecute(thread, task);
         }

         @Override
         protected void afterExecute(Runnable task, Throwable ex) {
            ThreadPoolTaskExecutor.this.afterExecute(task, ex);
         }
      };
      if (this.allowCoreThreadTimeOut) {
         executor.allowCoreThreadTimeOut(true);
      }

      if (this.prestartAllCoreThreads) {
         executor.prestartAllCoreThreads();
      }

      this.threadPoolExecutor = executor;
      return executor;
   }

   protected BlockingQueue<Runnable> createQueue(int queueCapacity) {
      return queueCapacity > 0 ? new LinkedBlockingQueue<>(queueCapacity) : new SynchronousQueue<>();
   }

   public ThreadPoolExecutor getThreadPoolExecutor() throws IllegalStateException {
      Assert.state(this.threadPoolExecutor != null, "ThreadPoolTaskExecutor not initialized");
      return this.threadPoolExecutor;
   }

   public int getPoolSize() {
      return this.threadPoolExecutor == null ? this.corePoolSize : this.threadPoolExecutor.getPoolSize();
   }

   public int getQueueSize() {
      return this.threadPoolExecutor == null ? 0 : this.threadPoolExecutor.getQueue().size();
   }

   public int getActiveCount() {
      return this.threadPoolExecutor == null ? 0 : this.threadPoolExecutor.getActiveCount();
   }

   public void execute(Runnable task) {
      Executor executor = this.getThreadPoolExecutor();

      try {
         executor.execute(task);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   public Future<?> submit(Runnable task) {
      ExecutorService executor = this.getThreadPoolExecutor();

      try {
         return executor.submit(task);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   public <T> Future<T> submit(Callable<T> task) {
      ExecutorService executor = this.getThreadPoolExecutor();

      try {
         return executor.submit(task);
      } catch (RejectedExecutionException ex) {
         throw new TaskRejectedException(executor, task, ex);
      }
   }

   @Override
   protected void cancelRemainingTask(Runnable task) {
      super.cancelRemainingTask(task);
      if (this.decoratedTaskMap.get(task) instanceof Future<?> future) {
         future.cancel(true);
      }
   }

   @Override
   protected void initiateEarlyShutdown() {
      if (this.strictEarlyShutdown) {
         super.initiateEarlyShutdown();
      }
   }
}
