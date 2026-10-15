package org.springframework.context;

public interface SmartLifecycle extends Lifecycle, Phased {
   int DEFAULT_PHASE = Integer.MAX_VALUE;

   default boolean isAutoStartup() {
      return true;
   }

   default boolean isPauseable() {
      return true;
   }

   default void stop(Runnable callback) {
      this.stop();
      callback.run();
   }

   @Override
   default int getPhase() {
      return Integer.MAX_VALUE;
   }
}
