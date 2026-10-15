package org.springframework.boot.logging.log4j2;

import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingSystemProperties;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.unit.DataSize;

public class Log4j2LoggingSystemProperties extends LoggingSystemProperties {
   public Log4j2LoggingSystemProperties(Environment environment) {
      super(environment);
   }

   public Log4j2LoggingSystemProperties(
      Environment environment, Function<@Nullable String, @Nullable String> defaultValueResolver, @Nullable BiConsumer<String, @Nullable String> setter
   ) {
      super(environment, defaultValueResolver, setter);
   }

   @Override
   protected void apply(@Nullable LogFile logFile, PropertyResolver resolver) {
      super.apply(logFile, resolver);
      this.applyRollingPolicyProperties(resolver);
   }

   private void applyRollingPolicyProperties(PropertyResolver resolver) {
      this.applyRollingPolicy(RollingPolicySystemProperty.FILE_NAME_PATTERN, resolver);
      this.applyRollingPolicy(RollingPolicySystemProperty.MAX_FILE_SIZE, resolver, DataSize.class);
      this.applyRollingPolicy(RollingPolicySystemProperty.MAX_HISTORY, resolver);
      this.applyRollingPolicy(RollingPolicySystemProperty.STRATEGY, resolver);
      this.applyRollingPolicy(RollingPolicySystemProperty.TIME_INTERVAL, resolver, Integer.class);
      this.applyRollingPolicy(RollingPolicySystemProperty.TIME_MODULATE, resolver, Boolean.class);
      this.applyRollingPolicy(RollingPolicySystemProperty.CRON, resolver);
   }

   private void applyRollingPolicy(RollingPolicySystemProperty property, PropertyResolver resolver) {
      this.applyRollingPolicy(property, resolver, String.class);
   }

   private <T> void applyRollingPolicy(RollingPolicySystemProperty property, PropertyResolver resolver, Class<T> type) {
      T value = this.getProperty(resolver, property.getApplicationPropertyName(), type);
      if (value != null) {
         String stringValue = String.valueOf(value instanceof DataSize dataSize ? dataSize.toBytes() : value);
         this.setSystemProperty(property.getEnvironmentVariableName(), stringValue);
      }
   }

   private <T> @Nullable T getProperty(PropertyResolver resolver, String key, Class<T> type) {
      try {
         return (T)resolver.getProperty(key, type);
      } catch (ConversionFailedException | ConverterNotFoundException ex) {
         if (type != DataSize.class) {
            throw ex;
         }

         String value = resolver.getProperty(key);
         return (T)(value != null ? DataSize.parse(value) : null);
      }
   }
}
