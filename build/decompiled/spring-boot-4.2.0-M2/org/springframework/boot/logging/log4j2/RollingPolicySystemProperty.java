package org.springframework.boot.logging.log4j2;

public enum RollingPolicySystemProperty {
   FILE_NAME_PATTERN("file-name-pattern"),
   MAX_FILE_SIZE("max-file-size"),
   MAX_HISTORY("max-history"),
   STRATEGY("strategy"),
   TIME_INTERVAL("time-interval"),
   TIME_MODULATE("time-modulate"),
   CRON("cron");

   private final String environmentVariableName = "LOG4J2_ROLLINGPOLICY_" + this.name();
   private final String applicationPropertyName;

   RollingPolicySystemProperty(String applicationPropertyName) {
      this.applicationPropertyName = "logging.log4j2.rollingpolicy." + applicationPropertyName;
   }

   public String getEnvironmentVariableName() {
      return this.environmentVariableName;
   }

   String getApplicationPropertyName() {
      return this.applicationPropertyName;
   }
}
