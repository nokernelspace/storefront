package org.springframework.core.log;

import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.NoOpLog;

final class CompositeLog implements Log {
   private static final Log NO_OP_LOG = new NoOpLog();
   private final List<Log> loggers;

   CompositeLog(List<Log> loggers) {
      this.loggers = loggers;
   }

   public boolean isFatalEnabled() {
      return this.isEnabled(Log::isFatalEnabled);
   }

   public boolean isErrorEnabled() {
      return this.isEnabled(Log::isErrorEnabled);
   }

   public boolean isWarnEnabled() {
      return this.isEnabled(Log::isWarnEnabled);
   }

   public boolean isInfoEnabled() {
      return this.isEnabled(Log::isInfoEnabled);
   }

   public boolean isDebugEnabled() {
      return this.isEnabled(Log::isDebugEnabled);
   }

   public boolean isTraceEnabled() {
      return this.isEnabled(Log::isTraceEnabled);
   }

   private boolean isEnabled(Predicate<Log> predicate) {
      return this.getLogger(predicate) != NO_OP_LOG;
   }

   public void fatal(Object message) {
      this.getLogger(Log::isFatalEnabled).fatal(message);
   }

   public void fatal(Object message, Throwable ex) {
      this.getLogger(Log::isFatalEnabled).fatal(message, ex);
   }

   public void error(Object message) {
      this.getLogger(Log::isErrorEnabled).error(message);
   }

   public void error(Object message, Throwable ex) {
      this.getLogger(Log::isErrorEnabled).error(message, ex);
   }

   public void warn(Object message) {
      this.getLogger(Log::isWarnEnabled).warn(message);
   }

   public void warn(Object message, Throwable ex) {
      this.getLogger(Log::isWarnEnabled).warn(message, ex);
   }

   public void info(Object message) {
      this.getLogger(Log::isInfoEnabled).info(message);
   }

   public void info(Object message, Throwable ex) {
      this.getLogger(Log::isInfoEnabled).info(message, ex);
   }

   public void debug(Object message) {
      this.getLogger(Log::isDebugEnabled).debug(message);
   }

   public void debug(Object message, Throwable ex) {
      this.getLogger(Log::isDebugEnabled).debug(message, ex);
   }

   public void trace(Object message) {
      this.getLogger(Log::isTraceEnabled).trace(message);
   }

   public void trace(Object message, Throwable ex) {
      this.getLogger(Log::isTraceEnabled).trace(message, ex);
   }

   private Log getLogger(Predicate<Log> predicate) {
      for (Log logger : this.loggers) {
         if (predicate.test(logger)) {
            return logger;
         }
      }

      return NO_OP_LOG;
   }
}
