package org.springframework.boot.logging.logback;

public enum RollingPolicySystemProperty {
   FILE_NAME_PATTERN("file-name-pattern"),
   CLEAN_HISTORY_ON_START("clean-history-on-start"),
   MAX_FILE_SIZE("max-file-size"),
   TOTAL_SIZE_CAP("total-size-cap"),
   MAX_HISTORY("max-history");

   private final String environmentVariableName = "LOGBACK_ROLLINGPOLICY_" + this.name();
   private final String applicationPropertyName;

   RollingPolicySystemProperty(String applicationPropertyName) {
      this.applicationPropertyName = "logging.logback.rollingpolicy." + applicationPropertyName;
   }

   public String getEnvironmentVariableName() {
      return this.environmentVariableName;
   }

   String getApplicationPropertyName() {
      return this.applicationPropertyName;
   }
}
