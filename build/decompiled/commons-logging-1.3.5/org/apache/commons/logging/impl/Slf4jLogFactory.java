package org.apache.commons.logging.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.spi.LocationAwareLogger;

public final class Slf4jLogFactory extends LogFactory {
   private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
   private static final String[] EMPTY_STRING_ARRAY = new String[0];
   private static final Marker MARKER = MarkerFactory.getMarker("COMMONS-LOGGING");
   private final ConcurrentMap<String, Log> loggers = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

   @Override
   public Object getAttribute(String name) {
      return this.attributes.get(name);
   }

   @Override
   public String[] getAttributeNames() {
      return this.attributes.keySet().toArray(EMPTY_STRING_ARRAY);
   }

   @Override
   public Log getInstance(Class<?> clazz) throws LogConfigurationException {
      return this.getInstance(clazz.getName());
   }

   @Override
   public Log getInstance(String name) {
      return this.loggers
         .computeIfAbsent(
            name,
            n -> {
               Logger logger = LoggerFactory.getLogger(n);
               return logger instanceof LocationAwareLogger
                  ? new Slf4jLogFactory.Slf4jLocationAwareLog((LocationAwareLogger)logger)
                  : new Slf4jLogFactory.Slf4jLog(logger);
            }
         );
   }

   @Override
   public void release() {
      ILoggerFactory factory = LoggerFactory.getILoggerFactory();

      try {
         factory.getClass().getMethod("stop").invoke(factory);
      } catch (ReflectiveOperationException var3) {
      }
   }

   @Override
   public void removeAttribute(String name) {
      this.attributes.remove(name);
   }

   @Override
   public void setAttribute(String name, Object value) {
      if (value != null) {
         this.attributes.put(name, value);
      } else {
         this.removeAttribute(name);
      }
   }

   private static final class Slf4jLocationAwareLog implements Log {
      private static final String FQCN = Slf4jLogFactory.Slf4jLocationAwareLog.class.getName();
      private final LocationAwareLogger logger;

      public Slf4jLocationAwareLog(LocationAwareLogger logger) {
         this.logger = logger;
      }

      @Override
      public void debug(Object message) {
         this.log(10, message, null);
      }

      @Override
      public void debug(Object message, Throwable t) {
         this.log(10, message, t);
      }

      @Override
      public void error(Object message) {
         this.log(40, message, null);
      }

      @Override
      public void error(Object message, Throwable t) {
         this.log(40, message, t);
      }

      @Override
      public void fatal(Object message) {
         this.error(message);
      }

      @Override
      public void fatal(Object message, Throwable t) {
         this.error(message, t);
      }

      @Override
      public void info(Object message) {
         this.log(20, message, null);
      }

      @Override
      public void info(Object message, Throwable t) {
         this.log(20, message, t);
      }

      @Override
      public boolean isDebugEnabled() {
         return this.logger.isDebugEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isErrorEnabled() {
         return this.logger.isErrorEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isFatalEnabled() {
         return this.isErrorEnabled();
      }

      @Override
      public boolean isInfoEnabled() {
         return this.logger.isInfoEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isTraceEnabled() {
         return this.logger.isTraceEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isWarnEnabled() {
         return this.logger.isWarnEnabled(Slf4jLogFactory.MARKER);
      }

      private void log(int level, Object message, Throwable t) {
         this.logger.log(Slf4jLogFactory.MARKER, FQCN, level, String.valueOf(message), Slf4jLogFactory.EMPTY_OBJECT_ARRAY, t);
      }

      @Override
      public void trace(Object message) {
         this.log(0, message, null);
      }

      @Override
      public void trace(Object message, Throwable t) {
         this.log(0, message, t);
      }

      @Override
      public void warn(Object message) {
         this.log(30, message, null);
      }

      @Override
      public void warn(Object message, Throwable t) {
         this.log(30, message, t);
      }
   }

   private static class Slf4jLog implements Log {
      private final Logger logger;

      public Slf4jLog(Logger logger) {
         this.logger = logger;
      }

      @Override
      public void debug(Object message) {
         this.logger.debug(Slf4jLogFactory.MARKER, String.valueOf(message));
      }

      @Override
      public void debug(Object message, Throwable t) {
         this.logger.debug(Slf4jLogFactory.MARKER, String.valueOf(message), t);
      }

      @Override
      public void error(Object message) {
         this.logger.error(Slf4jLogFactory.MARKER, String.valueOf(message));
      }

      @Override
      public void error(Object message, Throwable t) {
         this.logger.debug(Slf4jLogFactory.MARKER, String.valueOf(message), t);
      }

      @Override
      public void fatal(Object message) {
         this.error(message);
      }

      @Override
      public void fatal(Object message, Throwable t) {
         this.error(message, t);
      }

      @Override
      public void info(Object message) {
         this.logger.info(Slf4jLogFactory.MARKER, String.valueOf(message));
      }

      @Override
      public void info(Object message, Throwable t) {
         this.logger.info(Slf4jLogFactory.MARKER, String.valueOf(message), t);
      }

      @Override
      public boolean isDebugEnabled() {
         return this.logger.isDebugEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isErrorEnabled() {
         return this.logger.isErrorEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isFatalEnabled() {
         return this.isErrorEnabled();
      }

      @Override
      public boolean isInfoEnabled() {
         return this.logger.isInfoEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isTraceEnabled() {
         return this.logger.isTraceEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public boolean isWarnEnabled() {
         return this.logger.isWarnEnabled(Slf4jLogFactory.MARKER);
      }

      @Override
      public void trace(Object message) {
         this.logger.trace(Slf4jLogFactory.MARKER, String.valueOf(message));
      }

      @Override
      public void trace(Object message, Throwable t) {
         this.logger.trace(Slf4jLogFactory.MARKER, String.valueOf(message), t);
      }

      @Override
      public void warn(Object message) {
         this.logger.warn(Slf4jLogFactory.MARKER, String.valueOf(message));
      }

      @Override
      public void warn(Object message, Throwable t) {
         this.logger.warn(Slf4jLogFactory.MARKER, String.valueOf(message), t);
      }
   }
}
