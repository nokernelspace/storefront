package org.springframework.scheduling.concurrent;

import java.util.concurrent.ThreadFactory;
import org.springframework.util.CustomizableThreadCreator;

public class CustomizableThreadFactory extends CustomizableThreadCreator implements ThreadFactory {
   public CustomizableThreadFactory() {
   }

   public CustomizableThreadFactory(String threadNamePrefix) {
      super(threadNamePrefix);
   }

   @Override
   public Thread newThread(Runnable runnable) {
      return this.createThread(runnable);
   }
}
