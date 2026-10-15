package org.springframework.core.task;

import java.util.concurrent.ThreadFactory;

final class VirtualThreadDelegate {
   public VirtualThreadDelegate() {
      throw new UnsupportedOperationException("Virtual threads not supported on JDK <21");
   }

   public ThreadFactory virtualThreadFactory() {
      throw new UnsupportedOperationException();
   }

   public ThreadFactory virtualThreadFactory(String threadNamePrefix) {
      throw new UnsupportedOperationException();
   }

   public Thread newVirtualThread(String name, Runnable task) {
      throw new UnsupportedOperationException();
   }
}
