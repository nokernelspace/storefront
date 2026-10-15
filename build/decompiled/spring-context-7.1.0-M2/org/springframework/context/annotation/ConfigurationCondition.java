package org.springframework.context.annotation;

public interface ConfigurationCondition extends Condition {
   ConfigurationCondition.ConfigurationPhase getConfigurationPhase();

   enum ConfigurationPhase {
      PARSE_CONFIGURATION,
      REGISTER_BEAN;
   }
}
