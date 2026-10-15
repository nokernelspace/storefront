package org.springframework.boot.logging;

import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;

public enum LogLevel {
   TRACE(Log::trace),
   DEBUG(Log::debug),
   INFO(Log::info),
   WARN(Log::warn),
   ERROR(Log::error),
   FATAL(Log::fatal),
   OFF(null);

   private final LogLevel.@Nullable LogMethod logMethod;

   LogLevel(LogLevel.@Nullable LogMethod logMethod) {
      this.logMethod = logMethod;
   }

   public void log(Log logger, Object message) {
      this.log(logger, message, null);
   }

   public void log(@Nullable Log logger, Object message, @Nullable Throwable cause) {
      if (logger != null && this.logMethod != null) {
         this.logMethod.log(logger, message, cause);
      }
   }

   @FunctionalInterface
   private interface LogMethod {
      void log(Log logger, Object message, @Nullable Throwable cause);
   }
}
