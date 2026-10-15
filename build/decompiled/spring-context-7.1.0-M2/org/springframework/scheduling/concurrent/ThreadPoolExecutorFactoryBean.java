package org.springframework.scheduling.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;

public class ThreadPoolExecutorFactoryBean extends ExecutorConfigurationSupport implements FactoryBean<ExecutorService> {
   private int corePoolSize = 1;
   private int maxPoolSize = Integer.MAX_VALUE;
   private int keepAliveSeconds = 60;
   private int queueCapacity = Integer.MAX_VALUE;
   private boolean allowCoreThreadTimeOut = false;
   private boolean prestartAllCoreThreads = false;
   private boolean strictEarlyShutdown = false;
   private boolean exposeUnconfigurableExecutor = false;
   private @Nullable ExecutorService exposedExecutor;

   public void setCorePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
   }

   public void setMaxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
   }

   public void setKeepAliveSeconds(int keepAliveSeconds) {
      this.keepAliveSeconds = keepAliveSeconds;
   }

   public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
   }

   public void setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
      this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
   }

   public void setPrestartAllCoreThreads(boolean prestartAllCoreThreads) {
      this.prestartAllCoreThreads = prestartAllCoreThreads;
   }

   public void setStrictEarlyShutdown(boolean defaultEarlyShutdown) {
      this.strictEarlyShutdown = defaultEarlyShutdown;
   }

   public void setExposeUnconfigurableExecutor(boolean exposeUnconfigurableExecutor) {
      this.exposeUnconfigurableExecutor = exposeUnconfigurableExecutor;
   }

   @Override
   protected ExecutorService initializeExecutor(ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
      BlockingQueue<Runnable> queue = this.createQueue(this.queueCapacity);
      ThreadPoolExecutor executor = this.createExecutor(
         this.corePoolSize, this.maxPoolSize, this.keepAliveSeconds, queue, threadFactory, rejectedExecutionHandler
      );
      if (this.allowCoreThreadTimeOut) {
         executor.allowCoreThreadTimeOut(true);
      }

      if (this.prestartAllCoreThreads) {
         executor.prestartAllCoreThreads();
      }

      this.exposedExecutor = this.exposeUnconfigurableExecutor ? Executors.unconfigurableExecutorService(executor) : executor;
      return executor;
   }

   protected ThreadPoolExecutor createExecutor(
      int corePoolSize,
      int maxPoolSize,
      int keepAliveSeconds,
      BlockingQueue<Runnable> queue,
      ThreadFactory threadFactory,
      RejectedExecutionHandler rejectedExecutionHandler
   ) {
      return new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS, queue, threadFactory, rejectedExecutionHandler) {
         @Override
         protected void beforeExecute(Thread thread, Runnable task) {
            ThreadPoolExecutorFactoryBean.this.beforeExecute(thread, task);
         }

         @Override
         protected void afterExecute(Runnable task, Throwable ex) {
            ThreadPoolExecutorFactoryBean.this.afterExecute(task, ex);
         }
      };
   }

   protected BlockingQueue<Runnable> createQueue(int queueCapacity) {
      return queueCapacity > 0 ? new LinkedBlockingQueue<>(queueCapacity) : new SynchronousQueue<>();
   }

   @Override
   protected void initiateEarlyShutdown() {
      if (this.strictEarlyShutdown) {
         super.initiateEarlyShutdown();
      }
   }

   public @Nullable ExecutorService getObject() {
      return this.exposedExecutor;
   }

   public Class<? extends ExecutorService> getObjectType() {
      return (Class<? extends ExecutorService>)(this.exposedExecutor != null ? this.exposedExecutor.getClass() : ExecutorService.class);
   }

   public boolean isSingleton() {
      return true;
   }
}
