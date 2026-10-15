package org.springframework.boot.context.properties;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

class IncompatibleConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<IncompatibleConfigurationException> {
   protected FailureAnalysis analyze(Throwable rootFailure, IncompatibleConfigurationException cause) {
      String action = String.format("Review the docs for %s and change the configured values.", String.join(", ", cause.getIncompatibleKeys()));
      return new FailureAnalysis(cause.getMessage(), action, cause);
   }
}
