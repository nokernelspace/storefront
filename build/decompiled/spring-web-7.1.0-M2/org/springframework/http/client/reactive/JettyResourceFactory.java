package org.springframework.http.client.reactive;

import java.util.concurrent.Executor;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.ProcessorUtils;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.ThreadPool.SizedThreadPool;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

public class JettyResourceFactory implements InitializingBean, DisposableBean {
   private @Nullable Executor executor;
   private @Nullable ByteBufferPool byteBufferPool;
   private @Nullable Scheduler scheduler;
   private String threadPrefix = "jetty-http";

   public void setExecutor(@Nullable Executor executor) {
      this.executor = executor;
   }

   public void setByteBufferPool(@Nullable ByteBufferPool byteBufferPool) {
      this.byteBufferPool = byteBufferPool;
   }

   public void setScheduler(@Nullable Scheduler scheduler) {
      this.scheduler = scheduler;
   }

   public void setThreadPrefix(String threadPrefix) {
      Assert.notNull(threadPrefix, "Thread prefix is required");
      this.threadPrefix = threadPrefix;
   }

   public @Nullable Executor getExecutor() {
      return this.executor;
   }

   public @Nullable ByteBufferPool getByteBufferPool() {
      return this.byteBufferPool;
   }

   public @Nullable Scheduler getScheduler() {
      return this.scheduler;
   }

   public void afterPropertiesSet() throws Exception {
      String name = this.threadPrefix + "@" + Integer.toHexString(this.hashCode());
      if (this.executor == null) {
         QueuedThreadPool threadPool = new QueuedThreadPool();
         threadPool.setName(name);
         this.executor = threadPool;
      }

      if (this.byteBufferPool == null) {
         this.byteBufferPool = new ArrayByteBufferPool(
            0,
            2048,
            65536,
            this.executor instanceof SizedThreadPool sizedThreadPool ? sizedThreadPool.getMaxThreads() / 2 : ProcessorUtils.availableProcessors() * 2
         );
      }

      if (this.scheduler == null) {
         this.scheduler = new ScheduledExecutorScheduler(name + "-scheduler", false);
      }

      if (this.executor instanceof LifeCycle lifeCycle) {
         lifeCycle.start();
      }

      this.scheduler.start();
   }

   public void destroy() throws Exception {
      try {
         if (this.executor instanceof LifeCycle lifeCycle) {
            lifeCycle.stop();
         }
      } catch (Throwable var4) {
      }

      try {
         if (this.scheduler != null) {
            this.scheduler.stop();
         }
      } catch (Throwable var3) {
      }
   }
}
