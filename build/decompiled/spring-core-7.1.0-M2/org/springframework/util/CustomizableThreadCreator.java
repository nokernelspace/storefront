package org.springframework.util;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

public class CustomizableThreadCreator implements Serializable {
   private String threadNamePrefix;
   private int threadPriority = 5;
   private boolean daemon = false;
   private @Nullable ThreadGroup threadGroup;
   private final AtomicInteger threadCount = new AtomicInteger();

   public CustomizableThreadCreator() {
      this.threadNamePrefix = this.getDefaultThreadNamePrefix();
   }

   public CustomizableThreadCreator(@Nullable String threadNamePrefix) {
      this.threadNamePrefix = threadNamePrefix != null ? threadNamePrefix : this.getDefaultThreadNamePrefix();
   }

   public void setThreadNamePrefix(@Nullable String threadNamePrefix) {
      this.threadNamePrefix = threadNamePrefix != null ? threadNamePrefix : this.getDefaultThreadNamePrefix();
   }

   public String getThreadNamePrefix() {
      return this.threadNamePrefix;
   }

   public void setThreadPriority(int threadPriority) {
      this.threadPriority = threadPriority;
   }

   public int getThreadPriority() {
      return this.threadPriority;
   }

   public void setDaemon(boolean daemon) {
      this.daemon = daemon;
   }

   public boolean isDaemon() {
      return this.daemon;
   }

   public void setThreadGroupName(String name) {
      this.threadGroup = new ThreadGroup(name);
   }

   public void setThreadGroup(@Nullable ThreadGroup threadGroup) {
      this.threadGroup = threadGroup;
   }

   public @Nullable ThreadGroup getThreadGroup() {
      return this.threadGroup;
   }

   public Thread createThread(Runnable runnable) {
      Thread thread = new Thread(this.getThreadGroup(), runnable, this.nextThreadName());
      thread.setPriority(this.getThreadPriority());
      thread.setDaemon(this.isDaemon());
      return thread;
   }

   protected String nextThreadName() {
      return this.getThreadNamePrefix() + this.threadCount.incrementAndGet();
   }

   protected String getDefaultThreadNamePrefix() {
      return ClassUtils.getShortName(this.getClass()) + "-";
   }
}
