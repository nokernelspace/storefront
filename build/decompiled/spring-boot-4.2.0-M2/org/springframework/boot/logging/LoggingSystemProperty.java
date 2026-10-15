package org.springframework.boot.logging;

import org.jspecify.annotations.Nullable;

public enum LoggingSystemProperty {
   APPLICATION_NAME("APPLICATION_NAME", "spring.application.name", "logging.include-application-name"),
   APPLICATION_GROUP("APPLICATION_GROUP", "spring.application.group", "logging.include-application-group"),
   PID("PID"),
   LOG_FILE("LOG_FILE"),
   LOG_PATH("LOG_PATH"),
   CONSOLE_CHARSET("CONSOLE_LOG_CHARSET", "logging.charset.console"),
   FILE_CHARSET("FILE_LOG_CHARSET", "logging.charset.file"),
   CONSOLE_THRESHOLD("CONSOLE_LOG_THRESHOLD", "logging.threshold.console"),
   FILE_THRESHOLD("FILE_LOG_THRESHOLD", "logging.threshold.file"),
   EXCEPTION_CONVERSION_WORD("LOG_EXCEPTION_CONVERSION_WORD", "logging.exception-conversion-word"),
   CONSOLE_PATTERN("CONSOLE_LOG_PATTERN", "logging.pattern.console"),
   FILE_PATTERN("FILE_LOG_PATTERN", "logging.pattern.file"),
   CONSOLE_STRUCTURED_FORMAT("CONSOLE_LOG_STRUCTURED_FORMAT", "logging.structured.format.console"),
   FILE_STRUCTURED_FORMAT("FILE_LOG_STRUCTURED_FORMAT", "logging.structured.format.file"),
   LEVEL_PATTERN("LOG_LEVEL_PATTERN", "logging.pattern.level"),
   DATEFORMAT_PATTERN("LOG_DATEFORMAT_PATTERN", "logging.pattern.dateformat"),
   CORRELATION_PATTERN("LOG_CORRELATION_PATTERN", "logging.pattern.correlation");

   private final String environmentVariableName;
   private final @Nullable String applicationPropertyName;
   private final @Nullable String includePropertyName;

   LoggingSystemProperty(String environmentVariableName) {
      this(environmentVariableName, null);
   }

   LoggingSystemProperty(String environmentVariableName, @Nullable String applicationPropertyName) {
      this(environmentVariableName, applicationPropertyName, null);
   }

   LoggingSystemProperty(String environmentVariableName, @Nullable String applicationPropertyName, @Nullable String includePropertyName) {
      this.environmentVariableName = environmentVariableName;
      this.applicationPropertyName = applicationPropertyName;
      this.includePropertyName = includePropertyName;
   }

   public String getEnvironmentVariableName() {
      return this.environmentVariableName;
   }

   public @Nullable String getApplicationPropertyName() {
      return this.applicationPropertyName;
   }

   @Nullable String getIncludePropertyName() {
      return this.includePropertyName;
   }
}
