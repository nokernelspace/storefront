package org.springframework.boot.logging;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class DelegatingLoggingSystemFactory implements LoggingSystemFactory {
   private final @Nullable Function<ClassLoader, @Nullable List<LoggingSystemFactory>> delegates;

   DelegatingLoggingSystemFactory(@Nullable Function<ClassLoader, @Nullable List<LoggingSystemFactory>> delegates) {
      this.delegates = delegates;
   }

   @Override
   public @Nullable LoggingSystem getLoggingSystem(ClassLoader classLoader) {
      List<LoggingSystemFactory> delegates = this.delegates != null ? this.delegates.apply(classLoader) : null;
      if (delegates != null) {
         for (LoggingSystemFactory delegate : delegates) {
            LoggingSystem loggingSystem = delegate.getLoggingSystem(classLoader);
            if (loggingSystem != null) {
               return loggingSystem;
            }
         }
      }

      return null;
   }
}
