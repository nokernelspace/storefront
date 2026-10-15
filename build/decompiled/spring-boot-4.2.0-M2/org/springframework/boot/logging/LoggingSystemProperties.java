package org.springframework.boot.logging;

import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.system.ApplicationPid;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class LoggingSystemProperties {
   private static final BiConsumer<String, @Nullable String> systemPropertySetter = (name, value) -> {
      if (System.getProperty(name) == null && value != null) {
         System.setProperty(name, value);
      }
   };
   private final Environment environment;
   private final Function<@Nullable String, @Nullable String> defaultValueResolver;
   private final BiConsumer<String, @Nullable String> setter;

   public LoggingSystemProperties(Environment environment) {
      this(environment, null);
   }

   public LoggingSystemProperties(Environment environment, @Nullable BiConsumer<String, @Nullable String> setter) {
      this(environment, null, setter);
   }

   public LoggingSystemProperties(
      Environment environment,
      @Nullable Function<@Nullable String, @Nullable String> defaultValueResolver,
      @Nullable BiConsumer<String, @Nullable String> setter
   ) {
      Assert.notNull(environment, "'environment' must not be null");
      this.environment = environment;
      this.defaultValueResolver = defaultValueResolver != null ? defaultValueResolver : name -> null;
      this.setter = setter != null ? setter : systemPropertySetter;
   }

   protected @Nullable Console getConsole() {
      return System.console();
   }

   public final void apply() {
      this.apply(null);
   }

   public final void apply(@Nullable LogFile logFile) {
      PropertyResolver resolver = this.getPropertyResolver();
      this.apply(logFile, resolver);
   }

   private PropertyResolver getPropertyResolver() {
      if (this.environment instanceof ConfigurableEnvironment configurableEnvironment) {
         PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(configurableEnvironment.getPropertySources());
         resolver.setConversionService(configurableEnvironment.getConversionService());
         resolver.setIgnoreUnresolvableNestedPlaceholders(true);
         return resolver;
      } else {
         return this.environment;
      }
   }

   protected void apply(@Nullable LogFile logFile, PropertyResolver resolver) {
      this.setSystemProperty(LoggingSystemProperty.APPLICATION_NAME, resolver);
      this.setSystemProperty(LoggingSystemProperty.APPLICATION_GROUP, resolver);
      this.setSystemProperty(LoggingSystemProperty.PID, new ApplicationPid().toString());
      this.setSystemProperty(LoggingSystemProperty.CONSOLE_CHARSET, resolver, this.getDefaultConsoleCharset().name());
      this.setSystemProperty(LoggingSystemProperty.FILE_CHARSET, resolver, this.getDefaultFileCharset().name());
      this.setSystemProperty(LoggingSystemProperty.CONSOLE_THRESHOLD, resolver, this::thresholdMapper);
      this.setSystemProperty(LoggingSystemProperty.FILE_THRESHOLD, resolver, this::thresholdMapper);
      this.setSystemProperty(LoggingSystemProperty.EXCEPTION_CONVERSION_WORD, resolver);
      this.setSystemProperty(LoggingSystemProperty.CONSOLE_PATTERN, resolver);
      this.setSystemProperty(LoggingSystemProperty.FILE_PATTERN, resolver);
      this.setSystemProperty(LoggingSystemProperty.CONSOLE_STRUCTURED_FORMAT, resolver);
      this.setSystemProperty(LoggingSystemProperty.FILE_STRUCTURED_FORMAT, resolver);
      this.setSystemProperty(LoggingSystemProperty.LEVEL_PATTERN, resolver);
      this.setSystemProperty(LoggingSystemProperty.DATEFORMAT_PATTERN, resolver);
      this.setSystemProperty(LoggingSystemProperty.CORRELATION_PATTERN, resolver);
      if (logFile != null) {
         logFile.applyToSystemProperties();
      }

      if (!(Boolean)this.environment.getProperty("logging.console.enabled", Boolean.class, true)) {
         this.setSystemProperty(LoggingSystemProperty.CONSOLE_THRESHOLD.getEnvironmentVariableName(), "OFF");
      }
   }

   protected Charset getDefaultConsoleCharset() {
      Console console = this.getConsole();
      return console != null ? console.charset() : Charset.defaultCharset();
   }

   protected Charset getDefaultFileCharset() {
      return StandardCharsets.UTF_8;
   }

   private void setSystemProperty(LoggingSystemProperty property, PropertyResolver resolver) {
      this.setSystemProperty(property, resolver, i -> i);
   }

   private void setSystemProperty(LoggingSystemProperty property, PropertyResolver resolver, Function<@Nullable String, @Nullable String> mapper) {
      this.setSystemProperty(property, resolver, null, mapper);
   }

   private void setSystemProperty(LoggingSystemProperty property, PropertyResolver resolver, String defaultValue) {
      this.setSystemProperty(property, resolver, defaultValue, i -> i);
   }

   private void setSystemProperty(
      LoggingSystemProperty property, PropertyResolver resolver, @Nullable String defaultValue, Function<@Nullable String, @Nullable String> mapper
   ) {
      if (property.getIncludePropertyName() == null || (Boolean)resolver.getProperty(property.getIncludePropertyName(), Boolean.class, Boolean.TRUE)) {
         String applicationPropertyName = property.getApplicationPropertyName();
         String value = applicationPropertyName != null ? resolver.getProperty(applicationPropertyName) : null;
         value = value != null ? value : this.defaultValueResolver.apply(applicationPropertyName);
         value = value != null ? value : defaultValue;
         value = mapper.apply(value);
         this.setSystemProperty(property.getEnvironmentVariableName(), value);
         if (property == LoggingSystemProperty.APPLICATION_NAME && StringUtils.hasText(value)) {
            this.setSystemProperty("LOGGED_APPLICATION_NAME", "[%s] ".formatted(value));
         }
      }
   }

   private void setSystemProperty(LoggingSystemProperty property, String value) {
      this.setSystemProperty(property.getEnvironmentVariableName(), value);
   }

   private @Nullable String thresholdMapper(@Nullable String input) {
      return "false".equals(input) ? "OFF" : input;
   }

   protected final void setSystemProperty(String name, @Nullable String value) {
      this.setter.accept(name, value);
   }
}
