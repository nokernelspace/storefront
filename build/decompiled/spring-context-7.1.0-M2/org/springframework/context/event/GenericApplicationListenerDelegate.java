package org.springframework.context.event;

import java.util.function.Consumer;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.ResolvableType;

class GenericApplicationListenerDelegate<E extends ApplicationEvent> implements GenericApplicationListener {
   private final Class<E> supportedEventType;
   private final Consumer<E> consumer;

   GenericApplicationListenerDelegate(Class<E> supportedEventType, Consumer<E> consumer) {
      this.supportedEventType = supportedEventType;
      this.consumer = consumer;
   }

   @Override
   public void onApplicationEvent(ApplicationEvent event) {
      this.consumer.accept(this.supportedEventType.cast(event));
   }

   @Override
   public boolean supportsEventType(ResolvableType eventType) {
      return this.supportedEventType.isAssignableFrom(eventType.toClass());
   }
}
