package org.springframework.context;

import java.util.EventListener;
import java.util.function.Consumer;

@FunctionalInterface
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {
   void onApplicationEvent(E event);

   default boolean supportsAsyncExecution() {
      return true;
   }

   static <T> ApplicationListener<PayloadApplicationEvent<T>> forPayload(Consumer<T> consumer) {
      return event -> consumer.accept(event.getPayload());
   }
}
