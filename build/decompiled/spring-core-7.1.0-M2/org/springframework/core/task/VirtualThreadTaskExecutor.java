package org.springframework.core.task;

import java.util.concurrent.ThreadFactory;

public class VirtualThreadTaskExecutor implements AsyncTaskExecutor {
   private final ThreadFactory virtualThreadFactory;

   public VirtualThreadTaskExecutor() {
      this.virtualThreadFactory = new VirtualThreadDelegate().virtualThreadFactory();
   }

   public VirtualThreadTaskExecutor(String threadNamePrefix) {
      this.virtualThreadFactory = new VirtualThreadDelegate().virtualThreadFactory(threadNamePrefix);
   }

   public final ThreadFactory getVirtualThreadFactory() {
      return this.virtualThreadFactory;
   }

   @Override
   public void execute(Runnable task) {
      this.virtualThreadFactory.newThread(task).start();
   }
}
