package org.springframework.boot.context.event;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class ApplicationContextInitializedEvent extends SpringApplicationEvent {
   private final ConfigurableApplicationContext context;

   public ApplicationContextInitializedEvent(SpringApplication application, String[] args, ConfigurableApplicationContext context) {
      super(application, args);
      this.context = context;
   }

   public ConfigurableApplicationContext getApplicationContext() {
      return this.context;
   }
}
