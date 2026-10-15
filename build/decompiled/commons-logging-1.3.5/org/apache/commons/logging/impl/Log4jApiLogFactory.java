package org.apache.commons.logging.impl;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.spi.AbstractLoggerAdapter;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerAdapter;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.util.StackLocatorUtil;

public final class Log4jApiLogFactory extends LogFactory {
   private static final String[] EMPTY_ARRAY = new String[0];
   private static final Marker MARKER = MarkerManager.getMarker("COMMONS-LOGGING");
   private final LoggerAdapter<Log> adapter = new Log4jApiLogFactory.LogAdapter();
   private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

   @Override
   public Object getAttribute(String name) {
      return this.attributes.get(name);
   }

   @Override
   public String[] getAttributeNames() {
      return this.attributes.keySet().toArray(EMPTY_ARRAY);
   }

   @Override
   public Log getInstance(Class<?> clazz) {
      return this.getInstance(clazz.getName());
   }

   @Override
   public Log getInstance(String name) {
      return (Log)this.adapter.getLogger(name);
   }

   @Override
   public void release() {
      try {
         this.adapter.close();
      } catch (IOException var2) {
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

   private static final class Log4j2Log implements Log {
      private static final String FQCN = Log4jApiLogFactory.Log4j2Log.class.getName();
      private final ExtendedLogger logger;

      public Log4j2Log(ExtendedLogger logger) {
         this.logger = logger;
      }

      @Override
      public void debug(Object message) {
         this.logIfEnabled(Level.DEBUG, message, null);
      }

      @Override
      public void debug(Object message, Throwable t) {
         this.logIfEnabled(Level.DEBUG, message, t);
      }

      @Override
      public void error(Object message) {
         this.logIfEnabled(Level.ERROR, message, null);
      }

      @Override
      public void error(Object message, Throwable t) {
         this.logIfEnabled(Level.ERROR, message, t);
      }

      @Override
      public void fatal(Object message) {
         this.logIfEnabled(Level.FATAL, message, null);
      }

      @Override
      public void fatal(Object message, Throwable t) {
         this.logIfEnabled(Level.FATAL, message, t);
      }

      @Override
      public void info(Object message) {
         this.logIfEnabled(Level.INFO, message, null);
      }

      @Override
      public void info(Object message, Throwable t) {
         this.logIfEnabled(Level.INFO, message, t);
      }

      @Override
      public boolean isDebugEnabled() {
         return this.isEnabled(Level.DEBUG);
      }

      private boolean isEnabled(Level level) {
         return this.logger.isEnabled(level, Log4jApiLogFactory.MARKER, null);
      }

      @Override
      public boolean isErrorEnabled() {
         return this.isEnabled(Level.ERROR);
      }

      @Override
      public boolean isFatalEnabled() {
         return this.isEnabled(Level.FATAL);
      }

      @Override
      public boolean isInfoEnabled() {
         return this.isEnabled(Level.INFO);
      }

      @Override
      public boolean isTraceEnabled() {
         return this.isEnabled(Level.TRACE);
      }

      @Override
      public boolean isWarnEnabled() {
         return this.isEnabled(Level.WARN);
      }

      private void logIfEnabled(Level level, Object message, Throwable t) {
         if (message instanceof CharSequence) {
            this.logger.logIfEnabled(FQCN, level, Log4jApiLogFactory.MARKER, (CharSequence)message, t);
         } else {
            this.logger.logIfEnabled(FQCN, level, Log4jApiLogFactory.MARKER, message, t);
         }
      }

      @Override
      public void trace(Object message) {
         this.logIfEnabled(Level.TRACE, message, null);
      }

      @Override
      public void trace(Object message, Throwable t) {
         this.logIfEnabled(Level.TRACE, message, t);
      }

      @Override
      public void warn(Object message) {
         this.logIfEnabled(Level.WARN, message, null);
      }

      @Override
      public void warn(Object message, Throwable t) {
         this.logIfEnabled(Level.WARN, message, t);
      }
   }

   private static final class LogAdapter extends AbstractLoggerAdapter<Log> {
      private LogAdapter() {
      }

      protected LoggerContext getContext() {
         return this.getContext(LogManager.getFactory().isClassLoaderDependent() ? StackLocatorUtil.getCallerClass(LogFactory.class) : null);
      }

      protected Log newLogger(String name, LoggerContext context) {
         return new Log4jApiLogFactory.Log4j2Log(context.getLogger(name));
      }
   }
}
