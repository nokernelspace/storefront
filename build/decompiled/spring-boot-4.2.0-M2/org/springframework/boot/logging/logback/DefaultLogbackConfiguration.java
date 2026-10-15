package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.spi.ScanException;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.OptionHelper;
import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiElement;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.boot.logging.LogFile;
import org.springframework.util.StringUtils;

class DefaultLogbackConfiguration {
   private static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();
   private static final String NAME_AND_GROUP = "%esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}";
   private static final String DATETIME = "%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd'T'HH:mm:ss.SSSXXX}}";
   private static final String DEFAULT_CONSOLE_LOG_PATTERN = faint("%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd'T'HH:mm:ss.SSSXXX}}")
      + " "
      + colorByLevel("${LOG_LEVEL_PATTERN:-%5p}")
      + " "
      + magenta("${PID:-}")
      + " "
      + faint("--- %esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}[%15.15t] ${LOG_CORRELATION_PATTERN:-}")
      + cyan("%-40.40logger{39}")
      + " "
      + faint(":")
      + " %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}";
   static final String CONSOLE_LOG_PATTERN = "${CONSOLE_LOG_PATTERN:-" + DEFAULT_CONSOLE_LOG_PATTERN;
   private static final String DEFAULT_FILE_LOG_PATTERN = "%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd'T'HH:mm:ss.SSSXXX}} ${LOG_LEVEL_PATTERN:-%5p} ${PID:-} --- %esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}[%t] ${LOG_CORRELATION_PATTERN:-}%-40.40logger{39} : %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}";
   static final String FILE_LOG_PATTERN = "${FILE_LOG_PATTERN:-%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd'T'HH:mm:ss.SSSXXX}} ${LOG_LEVEL_PATTERN:-%5p} ${PID:-} --- %esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}[%t] ${LOG_CORRELATION_PATTERN:-}%-40.40logger{39} : %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}";
   private final @Nullable LogFile logFile;

   DefaultLogbackConfiguration(@Nullable LogFile logFile) {
      this.logFile = logFile;
   }

   void apply(LogbackConfigurator config) {
      config.getConfigurationLock().lock();

      try {
         this.defaults(config);
         Appender<ILoggingEvent> consoleAppender = this.consoleAppender(config);
         if (this.logFile != null) {
            Appender<ILoggingEvent> fileAppender = this.fileAppender(config, this.logFile.toString());
            config.root(Level.INFO, consoleAppender, fileAppender);
         } else {
            config.root(Level.INFO, consoleAppender);
         }
      } finally {
         config.getConfigurationLock().unlock();
      }
   }

   private void defaults(LogbackConfigurator config) {
      config.conversionRule("clr", ColorConverter.class, ColorConverter::new);
      config.conversionRule("correlationId", CorrelationIdConverter.class, CorrelationIdConverter::new);
      config.conversionRule("esb", EnclosedInSquareBracketsConverter.class, EnclosedInSquareBracketsConverter::new);
      config.conversionRule("wex", WhitespaceThrowableProxyConverter.class, WhitespaceThrowableProxyConverter::new);
      config.conversionRule("wEx", ExtendedWhitespaceThrowableProxyConverter.class, ExtendedWhitespaceThrowableProxyConverter::new);
      this.putProperty(config, "CONSOLE_LOG_PATTERN", CONSOLE_LOG_PATTERN);
      this.putProperty(config, "CONSOLE_LOG_CHARSET", "${CONSOLE_LOG_CHARSET:-" + this.getConsoleCharset() + "}");
      this.putProperty(config, "CONSOLE_LOG_THRESHOLD", "${CONSOLE_LOG_THRESHOLD:-TRACE}");
      this.putProperty(config, "CONSOLE_LOG_STRUCTURED_FORMAT", "${CONSOLE_LOG_STRUCTURED_FORMAT:-}");
      this.putProperty(
         config,
         "FILE_LOG_PATTERN",
         "${FILE_LOG_PATTERN:-%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd'T'HH:mm:ss.SSSXXX}} ${LOG_LEVEL_PATTERN:-%5p} ${PID:-} --- %esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}[%t] ${LOG_CORRELATION_PATTERN:-}%-40.40logger{39} : %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}"
      );
      this.putProperty(config, "FILE_LOG_CHARSET", "${FILE_LOG_CHARSET:-" + DEFAULT_CHARSET + "}");
      this.putProperty(config, "FILE_LOG_THRESHOLD", "${FILE_LOG_THRESHOLD:-TRACE}");
      this.putProperty(config, "FILE_LOG_STRUCTURED_FORMAT", "${FILE_LOG_STRUCTURED_FORMAT:-}");
      config.logger("org.apache.catalina.startup.DigesterFactory", Level.ERROR);
      config.logger("org.apache.catalina.util.LifecycleBase", Level.ERROR);
      config.logger("org.apache.coyote.http11.Http11NioProtocol", Level.WARN);
      config.logger("org.apache.sshd.common.util.SecurityUtils", Level.WARN);
      config.logger("org.apache.tomcat.util.net.NioSelectorPool", Level.WARN);
      config.logger("org.eclipse.jetty.util.component.AbstractLifeCycle", Level.ERROR);
      config.logger("org.hibernate.validator.internal.util.Version", Level.WARN);
      config.logger("org.springframework.boot.actuate.endpoint.jmx", Level.WARN);
   }

   private String getConsoleCharset() {
      Console console = this.getConsole();
      return console != null ? console.charset().name() : DEFAULT_CHARSET;
   }

   @Nullable Console getConsole() {
      return System.console();
   }

   void putProperty(LogbackConfigurator config, String name, String val) {
      config.getContext().putProperty(name, this.resolve(config, val));
   }

   private Appender<ILoggingEvent> consoleAppender(LogbackConfigurator config) {
      ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender();
      this.createAppender(config, appender, "CONSOLE");
      config.appender("CONSOLE", appender);
      return appender;
   }

   private Appender<ILoggingEvent> fileAppender(LogbackConfigurator config, String logFile) {
      RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender();
      this.createAppender(config, appender, "FILE");
      appender.setFile(logFile);
      this.setRollingPolicy(appender, config);
      config.appender("FILE", appender);
      return appender;
   }

   private void createAppender(LogbackConfigurator config, OutputStreamAppender<ILoggingEvent> appender, String type) {
      appender.addFilter(this.createThresholdFilter(config, type));
      Encoder<ILoggingEvent> encoder = this.createEncoder(config, type);
      appender.setEncoder(encoder);
      config.start(encoder);
   }

   private ThresholdFilter createThresholdFilter(LogbackConfigurator config, String type) {
      ThresholdFilter filter = new ThresholdFilter();
      filter.setLevel(this.resolve(config, "${" + type + "_LOG_THRESHOLD}"));
      filter.start();
      return filter;
   }

   private Encoder<ILoggingEvent> createEncoder(LogbackConfigurator config, String type) {
      Charset charset = this.resolveCharset(config, "${" + type + "_LOG_CHARSET}");
      String structuredLogFormat = this.resolve(config, "${" + type + "_LOG_STRUCTURED_FORMAT}");
      if (StringUtils.hasLength(structuredLogFormat)) {
         StructuredLogEncoder encoder = this.createStructuredLogEncoder(structuredLogFormat);
         encoder.setCharset(charset);
         return encoder;
      } else {
         PatternLayoutEncoder encoder = new PatternLayoutEncoder();
         encoder.setCharset(charset);
         encoder.setPattern(this.resolve(config, "${" + type + "_LOG_PATTERN}"));
         return encoder;
      }
   }

   private StructuredLogEncoder createStructuredLogEncoder(String format) {
      StructuredLogEncoder encoder = new StructuredLogEncoder();
      encoder.setFormat(format);
      return encoder;
   }

   private void setRollingPolicy(RollingFileAppender<ILoggingEvent> appender, LogbackConfigurator config) {
      SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy();
      rollingPolicy.setContext(config.getContext());
      rollingPolicy.setFileNamePattern(this.resolve(config, "${LOGBACK_ROLLINGPOLICY_FILE_NAME_PATTERN:-${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz}"));
      rollingPolicy.setCleanHistoryOnStart(this.resolveBoolean(config, "${LOGBACK_ROLLINGPOLICY_CLEAN_HISTORY_ON_START:-false}"));
      rollingPolicy.setMaxFileSize(this.resolveFileSize(config, "${LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE:-10MB}"));
      rollingPolicy.setTotalSizeCap(this.resolveFileSize(config, "${LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP:-0}"));
      rollingPolicy.setMaxHistory(this.resolveInt(config, "${LOGBACK_ROLLINGPOLICY_MAX_HISTORY:-7}"));
      appender.setRollingPolicy(rollingPolicy);
      rollingPolicy.setParent(appender);
      config.start(rollingPolicy);
   }

   private boolean resolveBoolean(LogbackConfigurator config, String val) {
      return Boolean.parseBoolean(this.resolve(config, val));
   }

   private int resolveInt(LogbackConfigurator config, String val) {
      return Integer.parseInt(this.resolve(config, val));
   }

   private FileSize resolveFileSize(LogbackConfigurator config, String val) {
      return FileSize.valueOf(this.resolve(config, val));
   }

   private Charset resolveCharset(LogbackConfigurator config, String val) {
      return Charset.forName(this.resolve(config, val));
   }

   private String resolve(LogbackConfigurator config, String val) {
      try {
         return OptionHelper.substVars(val, config.getContext());
      } catch (ScanException ex) {
         throw new RuntimeException(ex);
      }
   }

   private static String faint(String value) {
      return color(value, AnsiStyle.FAINT);
   }

   private static String cyan(String value) {
      return color(value, AnsiColor.CYAN);
   }

   private static String magenta(String value) {
      return color(value, AnsiColor.MAGENTA);
   }

   private static String colorByLevel(String value) {
      return "%clr(" + value + "){}";
   }

   private static String color(String value, AnsiElement ansiElement) {
      return "%clr(" + value + "){" + ColorConverter.getName(ansiElement) + "}";
   }
}
