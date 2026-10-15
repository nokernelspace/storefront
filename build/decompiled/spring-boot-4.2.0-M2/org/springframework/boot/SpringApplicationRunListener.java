package org.springframework.boot;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

public interface SpringApplicationRunListener {
   default void starting(ConfigurableBootstrapContext bootstrapContext) {
   }

   default void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
   }

   default void contextPrepared(ConfigurableApplicationContext context) {
   }

   default void contextLoaded(ConfigurableApplicationContext context) {
   }

   default void started(ConfigurableApplicationContext context, @Nullable Duration timeTaken) {
   }

   default void ready(ConfigurableApplicationContext context, @Nullable Duration timeTaken) {
   }

   default void failed(@Nullable ConfigurableApplicationContext context, Throwable exception) {
   }
}
