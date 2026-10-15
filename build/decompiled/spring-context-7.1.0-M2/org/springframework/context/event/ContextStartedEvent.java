package org.springframework.context.event;

import org.springframework.context.ApplicationContext;

public class ContextStartedEvent extends ApplicationContextEvent {
   public ContextStartedEvent(ApplicationContext source) {
      super(source);
   }
}
