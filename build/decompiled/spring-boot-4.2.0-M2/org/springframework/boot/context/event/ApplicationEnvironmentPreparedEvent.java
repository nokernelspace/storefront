package org.springframework.boot.context.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;

public class ApplicationEnvironmentPreparedEvent extends SpringApplicationEvent {
   private final ConfigurableBootstrapContext bootstrapContext;
   private final ConfigurableEnvironment environment;

   public ApplicationEnvironmentPreparedEvent(
      ConfigurableBootstrapContext bootstrapContext, SpringApplication application, String[] args, ConfigurableEnvironment environment
   ) {
      super(application, args);
      this.bootstrapContext = bootstrapContext;
      this.environment = environment;
   }

   public ConfigurableBootstrapContext getBootstrapContext() {
      return this.bootstrapContext;
   }

   public ConfigurableEnvironment getEnvironment() {
      return this.environment;
   }
}
