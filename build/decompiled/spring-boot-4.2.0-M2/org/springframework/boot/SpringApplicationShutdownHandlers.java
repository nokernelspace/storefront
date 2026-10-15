package org.springframework.boot;

public interface SpringApplicationShutdownHandlers {
   void add(Runnable action);

   void remove(Runnable action);
}
