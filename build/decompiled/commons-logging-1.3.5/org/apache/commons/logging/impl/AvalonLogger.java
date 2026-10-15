package org.apache.commons.logging.impl;

import java.util.Objects;
import org.apache.avalon.framework.logger.Logger;
import org.apache.commons.logging.Log;

@Deprecated
public class AvalonLogger implements Log {
   private static volatile Logger defaultLogger;
   private final transient Logger logger;

   public static void setDefaultLogger(Logger logger) {
      defaultLogger = logger;
   }

   public AvalonLogger(Logger logger) {
      this.logger = logger;
   }

   public AvalonLogger(String name) {
      Objects.requireNonNull(defaultLogger, "defaultLogger");
      this.logger = defaultLogger.getChildLogger(name);
   }

   @Override
   public void debug(Object message) {
      if (this.getLogger().isDebugEnabled()) {
         this.getLogger().debug(String.valueOf(message));
      }
   }

   @Override
   public void debug(Object message, Throwable t) {
      if (this.getLogger().isDebugEnabled()) {
         this.getLogger().debug(String.valueOf(message), t);
      }
   }

   @Override
   public void error(Object message) {
      if (this.getLogger().isErrorEnabled()) {
         this.getLogger().error(String.valueOf(message));
      }
   }

   @Override
   public void error(Object message, Throwable t) {
      if (this.getLogger().isErrorEnabled()) {
         this.getLogger().error(String.valueOf(message), t);
      }
   }

   @Override
   public void fatal(Object message) {
      if (this.getLogger().isFatalErrorEnabled()) {
         this.getLogger().fatalError(String.valueOf(message));
      }
   }

   @Override
   public void fatal(Object message, Throwable t) {
      if (this.getLogger().isFatalErrorEnabled()) {
         this.getLogger().fatalError(String.valueOf(message), t);
      }
   }

   public Logger getLogger() {
      return this.logger;
   }

   @Override
   public void info(Object message) {
      if (this.getLogger().isInfoEnabled()) {
         this.getLogger().info(String.valueOf(message));
      }
   }

   @Override
   public void info(Object message, Throwable t) {
      if (this.getLogger().isInfoEnabled()) {
         this.getLogger().info(String.valueOf(message), t);
      }
   }

   @Override
   public boolean isDebugEnabled() {
      return this.getLogger().isDebugEnabled();
   }

   @Override
   public boolean isErrorEnabled() {
      return this.getLogger().isErrorEnabled();
   }

   @Override
   public boolean isFatalEnabled() {
      return this.getLogger().isFatalErrorEnabled();
   }

   @Override
   public boolean isInfoEnabled() {
      return this.getLogger().isInfoEnabled();
   }

   @Override
   public boolean isTraceEnabled() {
      return this.getLogger().isDebugEnabled();
   }

   @Override
   public boolean isWarnEnabled() {
      return this.getLogger().isWarnEnabled();
   }

   @Override
   public void trace(Object message) {
      if (this.getLogger().isDebugEnabled()) {
         this.getLogger().debug(String.valueOf(message));
      }
   }

   @Override
   public void trace(Object message, Throwable t) {
      if (this.getLogger().isDebugEnabled()) {
         this.getLogger().debug(String.valueOf(message), t);
      }
   }

   @Override
   public void warn(Object message) {
      if (this.getLogger().isWarnEnabled()) {
         this.getLogger().warn(String.valueOf(message));
      }
   }

   @Override
   public void warn(Object message, Throwable t) {
      if (this.getLogger().isWarnEnabled()) {
         this.getLogger().warn(String.valueOf(message), t);
      }
   }
}
