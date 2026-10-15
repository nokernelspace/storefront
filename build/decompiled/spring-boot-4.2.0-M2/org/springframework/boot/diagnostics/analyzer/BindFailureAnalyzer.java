package org.springframework.boot.diagnostics.analyzer;

import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.util.StringUtils;

class BindFailureAnalyzer extends AbstractFailureAnalyzer<BindException> {
   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, BindException cause) {
      Throwable rootCause = cause.getCause();
      return !(rootCause instanceof BindValidationException) && !(rootCause instanceof UnboundConfigurationPropertiesException)
         ? this.analyzeGenericBindException(rootFailure, cause)
         : null;
   }

   private FailureAnalysis analyzeGenericBindException(Throwable rootFailure, BindException cause) {
      FailureAnalysis missingParametersAnalysis = MissingParameterNamesFailureAnalyzer.analyzeForMissingParameters(rootFailure);
      StringBuilder description = new StringBuilder(String.format("%s:%n", cause.getMessage()));
      ConfigurationProperty property = cause.getProperty();
      this.buildDescription(description, property);
      description.append(String.format("%n    Reason: %s", this.getMessage(cause)));
      if (missingParametersAnalysis != null) {
         MissingParameterNamesFailureAnalyzer.appendPossibility(description);
      }

      return this.getFailureAnalysis(description.toString(), cause, missingParametersAnalysis);
   }

   private void buildDescription(StringBuilder description, @Nullable ConfigurationProperty property) {
      if (property != null) {
         description.append(String.format("%n    Property: %s", property.getName()));
         description.append(String.format("%n    Value: \"%s\"", property.getValue()));
         description.append(String.format("%n    Origin: %s", property.getOrigin()));
      }
   }

   private String getMessage(BindException cause) {
      Throwable rootCause = this.getRootCause(cause.getCause());
      ConversionFailedException conversionFailure = this.findCause(cause, ConversionFailedException.class);
      if (conversionFailure != null) {
         String message = "failed to convert " + conversionFailure.getSourceType() + " to " + conversionFailure.getTargetType();
         if (rootCause != null) {
            message = message + " (caused by " + this.getExceptionTypeAndMessage(rootCause) + ")";
         }

         return message;
      } else {
         return rootCause != null && StringUtils.hasText(rootCause.getMessage())
            ? this.getExceptionTypeAndMessage(rootCause)
            : this.getExceptionTypeAndMessage(cause);
      }
   }

   private @Nullable Throwable getRootCause(@Nullable Throwable cause) {
      Throwable rootCause = cause;

      while (rootCause != null && rootCause.getCause() != null) {
         rootCause = rootCause.getCause();
      }

      return rootCause;
   }

   private String getExceptionTypeAndMessage(Throwable ex) {
      String message = ex.getMessage();
      return ex.getClass().getName() + (StringUtils.hasText(message) ? ": " + message : "");
   }

   private FailureAnalysis getFailureAnalysis(String description, BindException cause, @Nullable FailureAnalysis missingParametersAnalysis) {
      StringBuilder action = new StringBuilder("Update your application's configuration");
      Collection<String> validValues = this.findValidValues(cause);
      if (!validValues.isEmpty()) {
         action.append(String.format(". The following values are valid:%n"));
         validValues.forEach(value -> action.append(String.format("%n    %s", value)));
      }

      if (missingParametersAnalysis != null) {
         action.append(String.format("%n%n%s", missingParametersAnalysis.getAction()));
      }

      return new FailureAnalysis(description, action.toString(), cause);
   }

   private Collection<String> findValidValues(BindException ex) {
      ConversionFailedException conversionFailure = this.findCause(ex, ConversionFailedException.class);
      if (conversionFailure != null) {
         Object[] enumConstants = conversionFailure.getTargetType().getType().getEnumConstants();
         if (enumConstants != null) {
            return Stream.of(enumConstants).map(Object::toString).collect(Collectors.toCollection(TreeSet::new));
         }
      }

      return Collections.emptySet();
   }
}
