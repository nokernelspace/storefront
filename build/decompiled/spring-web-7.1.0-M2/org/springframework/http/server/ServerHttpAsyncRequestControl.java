package org.springframework.http.server;

public interface ServerHttpAsyncRequestControl {
   void start();

   void start(long timeout);

   boolean isStarted();

   void complete();

   boolean isCompleted();
}
