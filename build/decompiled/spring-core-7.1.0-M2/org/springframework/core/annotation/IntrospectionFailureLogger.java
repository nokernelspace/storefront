package org.springframework.core.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

enum IntrospectionFailureLogger {
   DEBUG {
      @Override
      public boolean isEnabled() {
         return IntrospectionFailureLogger.getLogger().isDebugEnabled();
      }

      @Override
      public void log(String message) {
         IntrospectionFailureLogger.getLogger().debug(message);
      }
   },
   INFO {
      @Override
      public boolean isEnabled() {
         return IntrospectionFailureLogger.getLogger().isInfoEnabled();
      }

      @Override
      public void log(String message) {
         IntrospectionFailureLogger.getLogger().info(message);
      }
   },
   WARN {
      @Override
      public boolean isEnabled() {
         return IntrospectionFailureLogger.getLogger().isWarnEnabled();
      }

      @Override
      public void log(String message) {
         IntrospectionFailureLogger.getLogger().warn(message);
      }
   };

   private static @Nullable Log logger;

   void log(String message, @Nullable Object source, Throwable ex) {
      String on = source != null ? " on " + source : "";
      this.log(message + on + ": " + ex);
   }

   abstract boolean isEnabled();

   abstract void log(String message);

   private static Log getLogger() {
      Log logger = IntrospectionFailureLogger.logger;
      if (logger == null) {
         logger = LogFactory.getLog(MergedAnnotation.class);
         IntrospectionFailureLogger.logger = logger;
      }

      return logger;
   }
}
