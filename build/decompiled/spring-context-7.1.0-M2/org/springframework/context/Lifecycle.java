package org.springframework.context;

public interface Lifecycle {
   void start();

   void stop();

   boolean isRunning();
}
