package org.springframework.boot.context.event;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class ApplicationReadyEvent extends SpringApplicationEvent {
   private final ConfigurableApplicationContext context;
   private final @Nullable Duration timeTaken;

   public ApplicationReadyEvent(SpringApplication application, String[] args, ConfigurableApplicationContext context, @Nullable Duration timeTaken) {
      super(application, args);
      this.context = context;
      this.timeTaken = timeTaken;
   }

   public ConfigurableApplicationContext getApplicationContext() {
      return this.context;
   }

   public @Nullable Duration getTimeTaken() {
      return this.timeTaken;
   }
}
