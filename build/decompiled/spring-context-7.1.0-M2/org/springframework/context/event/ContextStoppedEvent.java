package org.springframework.context.event;

import org.springframework.context.ApplicationContext;

public class ContextStoppedEvent extends ApplicationContextEvent {
   public ContextStoppedEvent(ApplicationContext source) {
      super(source);
   }
}
