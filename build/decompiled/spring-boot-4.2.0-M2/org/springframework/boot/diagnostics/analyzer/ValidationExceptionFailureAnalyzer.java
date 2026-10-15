package org.springframework.boot.diagnostics.analyzer;

import jakarta.validation.NoProviderFoundException;
import jakarta.validation.ValidationException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

class ValidationExceptionFailureAnalyzer extends AbstractFailureAnalyzer<ValidationException> {
   private static final String JAVAX_MISSING_IMPLEMENTATION_MESSAGE = "Unable to create a Configuration, because no Bean Validation provider could be found";
   private static final String JAKARTA_MISSING_IMPLEMENTATION_MESSAGE = "Unable to create a Configuration, because no Jakarta Bean Validation provider could be found";

   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, ValidationException cause) {
      String message = cause.getMessage();
      if (message == null) {
         message = "";
      }

      return !(cause instanceof NoProviderFoundException)
            && !message.startsWith("Unable to create a Configuration, because no Bean Validation provider could be found")
            && !message.startsWith("Unable to create a Configuration, because no Jakarta Bean Validation provider could be found")
         ? null
         : new FailureAnalysis(
            "The Bean Validation API is on the classpath but no implementation could be found",
            "Add an implementation, such as Hibernate Validator, to the classpath",
            cause
         );
   }
}
