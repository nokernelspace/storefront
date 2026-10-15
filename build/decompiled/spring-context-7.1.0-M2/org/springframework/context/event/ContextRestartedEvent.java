package org.springframework.context.event;

import org.springframework.context.ApplicationContext;

public class ContextRestartedEvent extends ContextStartedEvent {
   public ContextRestartedEvent(ApplicationContext source) {
      super(source);
   }
}
