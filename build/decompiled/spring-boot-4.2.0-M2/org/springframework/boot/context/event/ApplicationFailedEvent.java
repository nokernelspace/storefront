package org.springframework.boot.context.event;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class ApplicationFailedEvent extends SpringApplicationEvent {
   private final @Nullable ConfigurableApplicationContext context;
   private final Throwable exception;

   public ApplicationFailedEvent(SpringApplication application, String[] args, @Nullable ConfigurableApplicationContext context, Throwable exception) {
      super(application, args);
      this.context = context;
      this.exception = exception;
   }

   public @Nullable ConfigurableApplicationContext getApplicationContext() {
      return this.context;
   }

   public Throwable getException() {
      return this.exception;
   }
}
