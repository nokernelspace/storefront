package org.springframework.context.event;

import org.springframework.context.ApplicationContext;

public class ContextPausedEvent extends ContextStoppedEvent {
   public ContextPausedEvent(ApplicationContext source) {
      super(source);
   }
}
