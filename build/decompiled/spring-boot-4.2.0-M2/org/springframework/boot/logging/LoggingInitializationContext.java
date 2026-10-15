package org.springframework.boot.logging;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

public class LoggingInitializationContext {
   private final @Nullable ConfigurableEnvironment environment;

   public LoggingInitializationContext(@Nullable ConfigurableEnvironment environment) {
      this.environment = environment;
   }

   public @Nullable Environment getEnvironment() {
      return this.environment;
   }
}
