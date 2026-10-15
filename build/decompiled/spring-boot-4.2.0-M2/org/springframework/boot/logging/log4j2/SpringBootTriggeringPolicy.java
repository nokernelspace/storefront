package org.springframework.boot.logging.log4j2;

import java.util.Objects;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.CronTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.util.Builder;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionException;
import org.springframework.util.Assert;

@Plugin(name = "SpringBootTriggeringPolicy", category = "Core", elementType = "TriggeringPolicy", deferChildren = true, printObject = true)
public abstract class SpringBootTriggeringPolicy implements TriggeringPolicy {
   private SpringBootTriggeringPolicy() {
   }

   public void initialize(RollingFileManager manager) {
      throw new UnsupportedOperationException();
   }

   public boolean isTriggeringEvent(LogEvent logEvent) {
      throw new UnsupportedOperationException();
   }

   @PluginBuilderFactory
   public static SpringBootTriggeringPolicy.SpringBootTriggeringPolicyBuilder newBuilder() {
      return new SpringBootTriggeringPolicy.SpringBootTriggeringPolicyBuilder();
   }

   public static class SpringBootTriggeringPolicyBuilder implements Builder<TriggeringPolicy> {
      private static final String DEFAULT_STRATEGY = "size";
      private static final String DEFAULT_MAX_FILE_SIZE = "10MB";
      private static final String DEFAULT_TIME_INTERVAL = "1";
      private static final String DEFAULT_CRON_EXPRESSION = "0 0 0 * * ?";
      @PluginAttribute("strategy")
      private @Nullable String strategy;
      @PluginAttribute("maxFileSize")
      private @Nullable String maxFileSize;
      @PluginAttribute("timeInterval")
      private @Nullable Integer timeInterval;
      @PluginAttribute("timeModulate")
      private @Nullable Boolean timeModulate;
      @PluginAttribute("cronExpression")
      private @Nullable String cronExpression;
      @PluginConfiguration
      private @Nullable Configuration configuration;

      public TriggeringPolicy build() {
         RollingPolicyStrategy resolvedStrategy = getRollingPolicyStrategy();

         return (TriggeringPolicy)(switch (resolvedStrategy) {
            case TIME -> this.createTimePolicy();
            case SIZE_AND_TIME -> CompositeTriggeringPolicy.createPolicy(new TriggeringPolicy[]{this.createSizePolicy(), this.createTimePolicy()});
            case CRON -> this.createCronPolicy();
            case SIZE -> this.createSizePolicy();
         });
      }

      private static RollingPolicyStrategy getRollingPolicyStrategy() {
         String resolvedStrategy = getSystemProperty(RollingPolicySystemProperty.STRATEGY, "size");

         try {
            return Objects.requireNonNull(
               (RollingPolicyStrategy)ApplicationConversionService.getSharedInstance().convert(resolvedStrategy, RollingPolicyStrategy.class)
            );
         } catch (ConversionException ex) {
            throw new IllegalArgumentException("Unsupported rolling policy strategy '%s'".formatted(resolvedStrategy));
         }
      }

      private TriggeringPolicy createSizePolicy() {
         String size = getSystemProperty(RollingPolicySystemProperty.MAX_FILE_SIZE, "10MB");
         return SizeBasedTriggeringPolicy.createPolicy(size);
      }

      private TriggeringPolicy createTimePolicy() {
         int interval = Integer.parseInt(getSystemProperty(RollingPolicySystemProperty.TIME_INTERVAL, "1"));
         boolean modulate = Boolean.parseBoolean(getSystemProperty(RollingPolicySystemProperty.TIME_MODULATE, Boolean.FALSE.toString()));
         return TimeBasedTriggeringPolicy.newBuilder().setInterval(interval).setModulate(modulate).build();
      }

      private TriggeringPolicy createCronPolicy() {
         Assert.notNull(this.configuration, "configuration must not be null");
         Configuration configuration = this.configuration;
         String schedule = getSystemProperty(RollingPolicySystemProperty.CRON, "0 0 0 * * ?");
         return CronTriggeringPolicy.createPolicy(configuration, null, schedule);
      }

      private static String getSystemProperty(RollingPolicySystemProperty property, String fallback) {
         String value = System.getProperty(property.getEnvironmentVariableName());
         return value != null ? value : fallback;
      }

      SpringBootTriggeringPolicy.SpringBootTriggeringPolicyBuilder setConfiguration(Configuration configuration) {
         this.configuration = configuration;
         return this;
      }
   }
}
