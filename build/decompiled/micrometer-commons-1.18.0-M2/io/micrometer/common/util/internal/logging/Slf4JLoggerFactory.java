package io.micrometer.common.util.internal.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

public class Slf4JLoggerFactory extends InternalLoggerFactory {
   public static final InternalLoggerFactory INSTANCE = new Slf4JLoggerFactory();

   private Slf4JLoggerFactory() {
      if (LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory) {
         throw new NoClassDefFoundError("NOPLoggerFactory not supported");
      }
   }

   @Override
   public InternalLogger newInstance(String name) {
      return wrapLogger(LoggerFactory.getLogger(name));
   }

   static InternalLogger wrapLogger(Logger logger) {
      return logger instanceof LocationAwareLogger ? new LocationAwareSlf4JLogger((LocationAwareLogger)logger) : new Slf4JLogger(logger);
   }
}
