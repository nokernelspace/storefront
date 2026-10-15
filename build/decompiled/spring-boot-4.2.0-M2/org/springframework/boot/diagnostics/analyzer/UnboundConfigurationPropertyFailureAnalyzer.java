package org.springframework.boot.diagnostics.analyzer;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.util.Assert;

class UnboundConfigurationPropertyFailureAnalyzer extends AbstractFailureAnalyzer<UnboundConfigurationPropertiesException> {
   protected FailureAnalysis analyze(Throwable rootFailure, UnboundConfigurationPropertiesException cause) {
      BindException exception = this.findCause(rootFailure, BindException.class);
      Assert.state(exception != null, "BindException not found");
      return this.analyzeUnboundConfigurationPropertiesException(exception, cause);
   }

   private FailureAnalysis analyzeUnboundConfigurationPropertiesException(BindException cause, UnboundConfigurationPropertiesException exception) {
      StringBuilder description = new StringBuilder(String.format("Binding to target %s failed:%n", cause.getTarget()));

      for (ConfigurationProperty property : exception.getUnboundProperties()) {
         this.buildDescription(description, property);
         description.append(String.format("%n    Reason: %s", exception.getMessage()));
      }

      return this.getFailureAnalysis(description, cause);
   }

   private void buildDescription(StringBuilder description, @Nullable ConfigurationProperty property) {
      if (property != null) {
         description.append(String.format("%n    Property: %s", property.getName()));
         description.append(String.format("%n    Value: \"%s\"", property.getValue()));
         description.append(String.format("%n    Origin: %s", property.getOrigin()));
      }
   }

   private FailureAnalysis getFailureAnalysis(Object description, BindException cause) {
      return new FailureAnalysis(description.toString(), "Update your application's configuration", cause);
   }
}
