package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.status.InfoStatus;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

class DebugLogbackConfigurator extends LogbackConfigurator {
   DebugLogbackConfigurator(LoggerContext context) {
      super(context);
   }

   @Override
   <T extends Converter<?>> void conversionRule(String conversionWord, Class<T> converterClass, Supplier<T> converterSupplier) {
      this.info("Adding conversion rule of type '" + converterClass.getName() + "' for word '" + conversionWord + "'");
      super.conversionRule(conversionWord, converterClass, converterSupplier);
   }

   @Override
   void appender(String name, Appender<?> appender) {
      this.info("Adding appender '" + appender + "' named '" + name + "'");
      super.appender(name, appender);
   }

   @Override
   void logger(String name, @Nullable Level level, boolean additive, @Nullable Appender<ILoggingEvent> appender) {
      this.info("Configuring logger '" + name + "' with level '" + level + "'. Additive: " + additive);
      if (appender != null) {
         this.info("Adding appender '" + appender + "' to logger '" + name + "'");
      }

      super.logger(name, level, additive, appender);
   }

   @Override
   void start(LifeCycle lifeCycle) {
      this.info("Starting '" + lifeCycle + "'");
      super.start(lifeCycle);
   }

   private void info(String message) {
      this.getContext().getStatusManager().add(new InfoStatus(message, this));
   }
}
