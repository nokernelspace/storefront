package org.springframework.scheduling.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

public class ForkJoinPoolFactoryBean implements FactoryBean<ForkJoinPool>, InitializingBean, DisposableBean {
   private boolean commonPool = false;
   private int parallelism = Runtime.getRuntime().availableProcessors();
   private ForkJoinWorkerThreadFactory threadFactory = ForkJoinPool.defaultForkJoinWorkerThreadFactory;
   private @Nullable UncaughtExceptionHandler uncaughtExceptionHandler;
   private boolean asyncMode = false;
   private int awaitTerminationSeconds = 0;
   private @Nullable ForkJoinPool forkJoinPool;

   public void setCommonPool(boolean commonPool) {
      this.commonPool = commonPool;
   }

   public void setParallelism(int parallelism) {
      this.parallelism = parallelism;
   }

   public void setThreadFactory(ForkJoinWorkerThreadFactory threadFactory) {
      this.threadFactory = threadFactory;
   }

   public void setUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
      this.uncaughtExceptionHandler = uncaughtExceptionHandler;
   }

   public void setAsyncMode(boolean asyncMode) {
      this.asyncMode = asyncMode;
   }

   public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
      this.awaitTerminationSeconds = awaitTerminationSeconds;
   }

   public void afterPropertiesSet() {
      this.forkJoinPool = this.commonPool
         ? ForkJoinPool.commonPool()
         : new ForkJoinPool(this.parallelism, this.threadFactory, this.uncaughtExceptionHandler, this.asyncMode);
   }

   public @Nullable ForkJoinPool getObject() {
      return this.forkJoinPool;
   }

   public Class<?> getObjectType() {
      return ForkJoinPool.class;
   }

   public boolean isSingleton() {
      return true;
   }

   public void destroy() {
      if (this.forkJoinPool != null) {
         this.forkJoinPool.shutdown();
         if (this.awaitTerminationSeconds > 0) {
            try {
               this.forkJoinPool.awaitTermination(this.awaitTerminationSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }
   }
}
