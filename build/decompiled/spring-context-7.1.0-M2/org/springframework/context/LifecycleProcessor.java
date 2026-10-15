package org.springframework.context;

public interface LifecycleProcessor extends Lifecycle {
   default void onRefresh() {
      this.start();
   }

   default void onRestart() {
      this.stop();
      this.start();
   }

   default void onPause() {
      this.stop();
   }

   default void onClose() {
      this.stop();
   }
}
