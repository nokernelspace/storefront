package org.springframework.boot.logging.log4j2;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginLoggerContext;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.structured.CommonStructuredLogFormat;
import org.springframework.boot.logging.structured.ContextPairs;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLogFormatterFactory;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.boot.util.Instantiator;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

@Plugin(name = "StructuredLogLayout", category = "Core", elementType = "layout")
final class StructuredLogLayout extends AbstractStringLayout {
   private final StructuredLogFormatter<LogEvent> formatter;

   private StructuredLogLayout(Charset charset, StructuredLogFormatter<LogEvent> formatter) {
      super(charset);
      Assert.notNull(formatter, "'formatter' must not be null");
      this.formatter = formatter;
   }

   public String toSerializable(LogEvent event) {
      return this.formatter.format(event);
   }

   public byte[] toByteArray(LogEvent event) {
      return this.formatter.formatAsBytes(event, this.getCharset() != null ? this.getCharset() : StandardCharsets.UTF_8);
   }

   @PluginBuilderFactory
   static StructuredLogLayout.Builder newBuilder() {
      return new StructuredLogLayout.Builder();
   }

   static final class Builder implements org.apache.logging.log4j.core.util.Builder<StructuredLogLayout> {
      @PluginLoggerContext
      private LoggerContext loggerContext;
      @PluginBuilderAttribute
      private String format;
      @PluginBuilderAttribute
      private String charset = StandardCharsets.UTF_8.name();

      public StructuredLogLayout.Builder setFormat(String format) {
         this.format = format;
         return this;
      }

      public StructuredLogLayout.Builder setCharset(String charset) {
         this.charset = charset;
         return this;
      }

      public StructuredLogLayout build() {
         Charset charset = Charset.forName(this.charset);
         Environment environment = Log4J2LoggingSystem.getEnvironment(this.loggerContext);
         Assert.state(environment != null, "Unable to find Spring Environment in logger context");
         StructuredLogFormatter<LogEvent> formatter = new StructuredLogFormatterFactory<>(LogEvent.class, environment, null, this::addCommonFormatters)
            .get(this.format);
         return new StructuredLogLayout(charset, formatter);
      }

      private void addCommonFormatters(StructuredLogFormatterFactory.CommonFormatters<LogEvent> commonFormatters) {
         commonFormatters.add(CommonStructuredLogFormat.ELASTIC_COMMON_SCHEMA, this::createEcsFormatter);
         commonFormatters.add(CommonStructuredLogFormat.GRAYLOG_EXTENDED_LOG_FORMAT, this::createGraylogFormatter);
         commonFormatters.add(CommonStructuredLogFormat.LOGSTASH, this::createLogstashFormatter);
      }

      private ElasticCommonSchemaStructuredLogFormatter createEcsFormatter(Instantiator<?> instantiator) {
         Environment environment = instantiator.getArg(Environment.class);
         StackTracePrinter stackTracePrinter = instantiator.getArg(StackTracePrinter.class);
         ContextPairs contextPairs = instantiator.getArg(ContextPairs.class);
         StructuredLoggingJsonMembersCustomizer.Builder<?> jsonMembersCustomizerBuilder = instantiator.getArg(
            StructuredLoggingJsonMembersCustomizer.Builder.class
         );
         Assert.state(environment != null, "'environment' must not be null");
         Assert.state(contextPairs != null, "'contextPairs' must not be null");
         Assert.state(jsonMembersCustomizerBuilder != null, "'jsonMembersCustomizerBuilder' must not be null");
         return new ElasticCommonSchemaStructuredLogFormatter(environment, stackTracePrinter, contextPairs, jsonMembersCustomizerBuilder);
      }

      private GraylogExtendedLogFormatStructuredLogFormatter createGraylogFormatter(Instantiator<?> instantiator) {
         Environment environment = instantiator.getArg(Environment.class);
         StackTracePrinter stackTracePrinter = instantiator.getArg(StackTracePrinter.class);
         ContextPairs contextPairs = instantiator.getArg(ContextPairs.class);
         StructuredLoggingJsonMembersCustomizer<?> jsonMembersCustomizer = instantiator.getArg(StructuredLoggingJsonMembersCustomizer.class);
         Assert.state(environment != null, "'environment' must not be null");
         Assert.state(contextPairs != null, "'contextPairs' must not be null");
         return new GraylogExtendedLogFormatStructuredLogFormatter(environment, stackTracePrinter, contextPairs, jsonMembersCustomizer);
      }

      private LogstashStructuredLogFormatter createLogstashFormatter(Instantiator<?> instantiator) {
         StackTracePrinter stackTracePrinter = instantiator.getArg(StackTracePrinter.class);
         ContextPairs contextPairs = instantiator.getArg(ContextPairs.class);
         StructuredLoggingJsonMembersCustomizer<?> jsonMembersCustomizer = instantiator.getArg(StructuredLoggingJsonMembersCustomizer.class);
         Assert.state(contextPairs != null, "'contextPairs' must not be null");
         return new LogstashStructuredLogFormatter(stackTracePrinter, contextPairs, jsonMembersCustomizer);
      }
   }
}
