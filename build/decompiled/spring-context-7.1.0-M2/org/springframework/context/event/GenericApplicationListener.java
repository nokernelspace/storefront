package org.springframework.context.event;

import java.util.function.Consumer;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.ResolvableType;

public interface GenericApplicationListener extends SmartApplicationListener {
   @Override
   default boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
      return this.supportsEventType(ResolvableType.forClass(eventType));
   }

   boolean supportsEventType(ResolvableType eventType);

   static <E extends ApplicationEvent> GenericApplicationListener forEventType(Class<E> eventType, Consumer<E> consumer) {
      return new GenericApplicationListenerDelegate<>(eventType, consumer);
   }
}
