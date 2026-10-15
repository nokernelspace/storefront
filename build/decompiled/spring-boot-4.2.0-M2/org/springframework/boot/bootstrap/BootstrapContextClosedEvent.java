package org.springframework.boot.bootstrap;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;

public class BootstrapContextClosedEvent extends ApplicationEvent {
   private final ConfigurableApplicationContext applicationContext;

   BootstrapContextClosedEvent(BootstrapContext source, ConfigurableApplicationContext applicationContext) {
      super(source);
      this.applicationContext = applicationContext;
   }

   public BootstrapContext getBootstrapContext() {
      return (BootstrapContext)this.source;
   }

   public ConfigurableApplicationContext getApplicationContext() {
      return this.applicationContext;
   }
}
