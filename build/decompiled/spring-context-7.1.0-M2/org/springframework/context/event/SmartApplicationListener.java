package org.springframework.context.event;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

public interface SmartApplicationListener extends ApplicationListener<ApplicationEvent>, Ordered {
   boolean supportsEventType(Class<? extends ApplicationEvent> eventType);

   default boolean supportsSourceType(@Nullable Class<?> sourceType) {
      return true;
   }

   default int getOrder() {
      return Integer.MAX_VALUE;
   }

   default String getListenerId() {
      return "";
   }
}
