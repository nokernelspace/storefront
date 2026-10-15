package org.springframework.boot.logging;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.support.SpringFactoriesLoader;

public interface LoggingSystemFactory {
   @Nullable LoggingSystem getLoggingSystem(ClassLoader classLoader);

   static LoggingSystemFactory fromSpringFactories() {
      return new DelegatingLoggingSystemFactory(classLoader -> SpringFactoriesLoader.loadFactories(LoggingSystemFactory.class, classLoader));
   }
}
