package org.springframework.boot.diagnostics.analyzer;

import org.springframework.boot.AotInitializerNotFoundException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

class AotInitializerNotFoundFailureAnalyzer extends AbstractFailureAnalyzer<AotInitializerNotFoundException> {
   protected FailureAnalysis analyze(Throwable rootFailure, AotInitializerNotFoundException cause) {
      return new FailureAnalysis(
         cause.getMessage(),
         "Consider the following:\n\tDid you build the application with enabled AOT processing?\n"
            + "\tIs the main class %s correct?\n".formatted(cause.getMainClass().getName())
            + "\tIf you want to run the application in regular mode, remove the system property 'spring.aot.enabled'",
         cause
      );
   }
}
