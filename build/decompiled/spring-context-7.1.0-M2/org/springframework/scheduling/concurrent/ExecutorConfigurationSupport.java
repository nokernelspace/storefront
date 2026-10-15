package org.springframework.scheduling.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.task.VirtualThreadTaskExecutor;

public abstract class ExecutorConfigurationSupport
   extends CustomizableThreadFactory
   implements BeanNameAware,
   ApplicationContextAware,
   InitializingBean,
   DisposableBean,
   SmartLifecycle,
   ApplicationListener<ContextClosedEvent> {
   public static final int DEFAULT_PHASE = 1073741823;
   protected final Log logger = LogFactory.getLog(this.getClass());
   private boolean virtualThreads = false;
   private ThreadFactory threadFactory = this;
   private boolean threadNamePrefixSet = false;
   private RejectedExecutionHandler rejectedExecutionHandler = new AbortPolicy();
   private boolean acceptTasksAfterContextClose = false;
   private boolean waitForTasksToCompleteOnShutdown = false;
   private long awaitTerminationMillis = 0L;
   private int phase = 1073741823;
   private @Nullable String beanName;
   private @Nullable ApplicationContext applicationContext;
   private @Nullable ExecutorService executor;
   private @Nullable ExecutorLifecycleDelegate lifecycleDelegate;
   private volatile boolean lateShutdown;

   public void setVirtualThreads(boolean virtualThreads) {
      this.virtualThreads = virtualThreads;
      this.threadFactory = this;
   }

   public void setThreadFactory(@Nullable ThreadFactory threadFactory) {
      this.threadFactory = threadFactory != null ? threadFactory : this;
      this.virtualThreads = false;
   }

   public void setThreadNamePrefix(@Nullable String threadNamePrefix) {
      super.setThreadNamePrefix(threadNamePrefix);
      this.threadNamePrefixSet = true;
   }

   public void setRejectedExecutionHandler(@Nullable RejectedExecutionHandler rejectedExecutionHandler) {
      this.rejectedExecutionHandler = rejectedExecutionHandler != null ? rejectedExecutionHandler : new AbortPolicy();
   }

   public void setAcceptTasksAfterContextClose(boolean acceptTasksAfterContextClose) {
      this.acceptTasksAfterContextClose = acceptTasksAfterContextClose;
   }

   public void setWaitForTasksToCompleteOnShutdown(boolean waitForJobsToCompleteOnShutdown) {
      this.waitForTasksToCompleteOnShutdown = waitForJobsToCompleteOnShutdown;
   }

   public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
      this.awaitTerminationMillis = awaitTerminationSeconds * 1000L;
   }

   public void setAwaitTerminationMillis(long awaitTerminationMillis) {
      this.awaitTerminationMillis = awaitTerminationMillis;
   }

   public void setPhase(int phase) {
      this.phase = phase;
   }

   @Override
   public int getPhase() {
      return this.phase;
   }

   public void setBeanName(String name) {
      this.beanName = name;
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   public void afterPropertiesSet() {
      this.initialize();
   }

   public void initialize() {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Initializing ExecutorService" + (this.beanName != null ? " '" + this.beanName + "'" : ""));
      }

      if (!this.threadNamePrefixSet && this.beanName != null) {
         this.setThreadNamePrefix(this.beanName + "-");
      }

      ThreadFactory factory = this.virtualThreads ? new VirtualThreadTaskExecutor(this.getThreadNamePrefix()).getVirtualThreadFactory() : this.threadFactory;
      this.executor = this.initializeExecutor(factory, this.rejectedExecutionHandler);
      this.lifecycleDelegate = new ExecutorLifecycleDelegate(this.executor);
   }

   protected abstract ExecutorService initializeExecutor(ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler);

   public void destroy() {
      this.shutdown();
   }

   public void initiateShutdown() {
      if (this.executor != null) {
         this.executor.shutdown();
      }
   }

   public void shutdown() {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Shutting down ExecutorService" + (this.beanName != null ? " '" + this.beanName + "'" : ""));
      }

      if (this.executor != null) {
         if (this.waitForTasksToCompleteOnShutdown) {
            this.executor.shutdown();
         } else {
            for (Runnable remainingTask : this.executor.shutdownNow()) {
               this.cancelRemainingTask(remainingTask);
            }
         }

         this.awaitTerminationIfNecessary(this.executor);
      }
   }

   protected void cancelRemainingTask(Runnable task) {
      if (task instanceof Future<?> future) {
         future.cancel(true);
      }
   }

   private void awaitTerminationIfNecessary(ExecutorService executor) {
      if (this.awaitTerminationMillis > 0L) {
         try {
            if (!executor.awaitTermination(this.awaitTerminationMillis, TimeUnit.MILLISECONDS) && this.logger.isWarnEnabled()) {
               this.logger.warn("Timed out while waiting for executor" + (this.beanName != null ? " '" + this.beanName + "'" : "") + " to terminate");
            }
         } catch (InterruptedException ex) {
            if (this.logger.isWarnEnabled()) {
               this.logger.warn("Interrupted while waiting for executor" + (this.beanName != null ? " '" + this.beanName + "'" : "") + " to terminate");
            }

            Thread.currentThread().interrupt();
         }
      }
   }

   @Override
   public void start() {
      if (this.lifecycleDelegate != null) {
         this.lifecycleDelegate.start();
      }
   }

   @Override
   public void stop() {
      if (this.lifecycleDelegate != null && !this.lateShutdown) {
         this.lifecycleDelegate.stop();
      }
   }

   @Override
   public void stop(Runnable callback) {
      if (this.lifecycleDelegate != null && !this.lateShutdown) {
         this.lifecycleDelegate.stop(callback);
      } else {
         callback.run();
      }
   }

   @Override
   public boolean isRunning() {
      return this.lifecycleDelegate != null && this.lifecycleDelegate.isRunning();
   }

   protected void beforeExecute(Thread thread, Runnable task) {
      if (this.lifecycleDelegate != null) {
         this.lifecycleDelegate.beforeExecute(thread);
      }
   }

   protected void afterExecute(Runnable task, @Nullable Throwable ex) {
      if (this.lifecycleDelegate != null) {
         this.lifecycleDelegate.afterExecute();
      }
   }

   public void onApplicationEvent(ContextClosedEvent event) {
      if (event.getApplicationContext() == this.applicationContext) {
         if (!this.acceptTasksAfterContextClose && !this.waitForTasksToCompleteOnShutdown) {
            if (this.lifecycleDelegate != null) {
               this.lifecycleDelegate.markShutdown();
            }

            this.initiateEarlyShutdown();
         } else {
            this.lateShutdown = true;
         }
      }
   }

   protected void initiateEarlyShutdown() {
      this.initiateShutdown();
   }
}
