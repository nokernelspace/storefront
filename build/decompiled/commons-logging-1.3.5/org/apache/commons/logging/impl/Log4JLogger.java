package org.apache.commons.logging.impl;

import java.io.Serializable;
import org.apache.commons.logging.Log;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

@Deprecated
public class Log4JLogger implements Log, Serializable {
   private static final long serialVersionUID = 5160705895411730424L;
   private static final String FQCN = Log4JLogger.class.getName();
   private static final Priority traceLevel;
   private transient volatile Logger logger;
   private final String name;

   public Log4JLogger() {
      this.name = null;
   }

   public Log4JLogger(Logger logger) {
      if (logger == null) {
         throw new IllegalArgumentException("Warning - null logger in constructor; possible Log4j misconfiguration.");
      }

      this.name = logger.getName();
      this.logger = logger;
   }

   public Log4JLogger(String name) {
      this.name = name;
      this.logger = this.getLogger();
   }

   @Override
   public void debug(Object message) {
      this.getLogger().log(FQCN, Level.DEBUG, message, null);
   }

   @Override
   public void debug(Object message, Throwable t) {
      this.getLogger().log(FQCN, Level.DEBUG, message, t);
   }

   @Override
   public void error(Object message) {
      this.getLogger().log(FQCN, Level.ERROR, message, null);
   }

   @Override
   public void error(Object message, Throwable t) {
      this.getLogger().log(FQCN, Level.ERROR, message, t);
   }

   @Override
   public void fatal(Object message) {
      this.getLogger().log(FQCN, Level.FATAL, message, null);
   }

   @Override
   public void fatal(Object message, Throwable t) {
      this.getLogger().log(FQCN, Level.FATAL, message, t);
   }

   public Logger getLogger() {
      Logger result = this.logger;
      if (result == null) {
         synchronized (this) {
            result = this.logger;
            if (result == null) {
               this.logger = result = Logger.getLogger(this.name);
            }
         }
      }

      return result;
   }

   @Override
   public void info(Object message) {
      this.getLogger().log(FQCN, Level.INFO, message, null);
   }

   @Override
   public void info(Object message, Throwable t) {
      this.getLogger().log(FQCN, Level.INFO, message, t);
   }

   @Override
   public boolean isDebugEnabled() {
      return this.getLogger().isDebugEnabled();
   }

   @Override
   public boolean isErrorEnabled() {
      return this.getLogger().isEnabledFor(Level.ERROR);
   }

   @Override
   public boolean isFatalEnabled() {
      return this.getLogger().isEnabledFor(Level.FATAL);
   }

   @Override
   public boolean isInfoEnabled() {
      return this.getLogger().isInfoEnabled();
   }

   @Override
   public boolean isTraceEnabled() {
      return this.getLogger().isEnabledFor(traceLevel);
   }

   @Override
   public boolean isWarnEnabled() {
      return this.getLogger().isEnabledFor(Level.WARN);
   }

   @Override
   public void trace(Object message) {
      this.getLogger().log(FQCN, traceLevel, message, null);
   }

   @Override
   public void trace(Object message, Throwable t) {
      this.getLogger().log(FQCN, traceLevel, message, t);
   }

   @Override
   public void warn(Object message) {
      this.getLogger().log(FQCN, Level.WARN, message, null);
   }

   @Override
   public void warn(Object message, Throwable t) {
      this.getLogger().log(FQCN, Level.WARN, message, t);
   }

   static {
      if (!Priority.class.isAssignableFrom(Level.class)) {
         throw new InstantiationError("Log4J 1.2 not available");
      }

      Priority _traceLevel;
      try {
         _traceLevel = (Priority)Level.class.getDeclaredField("TRACE").get(null);
      } catch (Exception ex) {
         _traceLevel = Level.DEBUG;
      }

      traceLevel = _traceLevel;
   }
}
