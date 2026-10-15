package org.springframework.boot.diagnostics.analyzer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

class InvalidConfigurationPropertyValueFailureAnalyzer extends AbstractFailureAnalyzer<InvalidConfigurationPropertyValueException> {
   private final @Nullable ConfigurableEnvironment environment;

   InvalidConfigurationPropertyValueFailureAnalyzer(@Nullable Environment environment) {
      this.environment = (ConfigurableEnvironment)environment;
   }

   protected FailureAnalysis analyze(Throwable rootFailure, InvalidConfigurationPropertyValueException cause) {
      List<InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor> descriptors = this.getDescriptors(cause.getName());
      if (descriptors.isEmpty()) {
         descriptors = List.of(new InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor(null, cause.getValue(), null));
      }

      StringBuilder description = new StringBuilder();
      this.appendDetails(description, cause, descriptors);
      this.appendReason(description, cause);
      this.appendAdditionalProperties(description, descriptors);
      return new FailureAnalysis(description.toString(), this.getAction(cause), cause);
   }

   private List<InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor> getDescriptors(String propertyName) {
      Set<Origin> seen = new HashSet<>();
      return this.getPropertySources()
         .filter(source -> source.containsProperty(propertyName))
         .map(source -> InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor.get((PropertySource<?>)source, propertyName))
         .filter(descriptor -> seen.add(this.getOrigin(descriptor)))
         .toList();
   }

   private @Nullable Origin getOrigin(InvalidConfigurationPropertyValueFailureAnalyzer.@Nullable Descriptor descriptor) {
      if (descriptor == null) {
         return null;
      }

      Origin origin = descriptor.origin;
      if (origin instanceof PropertySourceOrigin propertySourceOrigin) {
         origin = propertySourceOrigin.getOrigin();
      }

      return origin;
   }

   private Stream<PropertySource<?>> getPropertySources() {
      return this.environment != null ? this.environment.getPropertySources().stream() : Stream.empty();
   }

   private void appendDetails(
      StringBuilder message, InvalidConfigurationPropertyValueException cause, List<InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor> descriptors
   ) {
      InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor mainDescriptor = descriptors.get(0);
      message.append("Invalid value '").append(mainDescriptor.getValue()).append("' for configuration property '");
      message.append(cause.getName()).append("'");
      mainDescriptor.appendOrigin(message);
      message.append(".");
   }

   private void appendReason(StringBuilder message, InvalidConfigurationPropertyValueException cause) {
      if (StringUtils.hasText(cause.getReason())) {
         message.append(String.format(" Validation failed for the following reason:%n%n"));
         message.append(cause.getReason());
      } else {
         message.append(" No reason was provided.");
      }
   }

   private void appendAdditionalProperties(StringBuilder message, List<InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor> descriptors) {
      List<InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor> others = descriptors.subList(1, descriptors.size());
      if (!others.isEmpty()) {
         message.append(
            String.format("%n%nAdditionally, this property is also set in the following property %s:%n%n", others.size() > 1 ? "sources" : "source")
         );

         for (InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor other : others) {
            message.append("\t- In '").append(other.getPropertySource()).append("'");
            message.append(" with the value '").append(other.getValue()).append("'");
            other.appendOrigin(message);
            message.append(String.format(".%n"));
         }
      }
   }

   private String getAction(InvalidConfigurationPropertyValueException cause) {
      StringBuilder action = new StringBuilder();
      action.append("Review the value of the property");
      if (cause.getReason() != null) {
         action.append(" with the provided reason");
      }

      action.append(".");
      return action.toString();
   }

   private static final class Descriptor {
      private final @Nullable String propertySource;
      private final @Nullable Object value;
      private final @Nullable Origin origin;

      private Descriptor(@Nullable String propertySource, @Nullable Object value, @Nullable Origin origin) {
         this.propertySource = propertySource;
         this.value = value;
         this.origin = origin;
      }

      @Nullable String getPropertySource() {
         return this.propertySource;
      }

      @Nullable Object getValue() {
         return this.value;
      }

      void appendOrigin(StringBuilder message) {
         if (this.origin != null) {
            message.append(" (originating from '").append(this.origin).append("')");
         }
      }

      static InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor get(PropertySource<?> source, String propertyName) {
         Object value = source.getProperty(propertyName);
         Origin origin = OriginLookup.getOrigin(source, propertyName);
         return new InvalidConfigurationPropertyValueFailureAnalyzer.Descriptor(source.getName(), value, origin);
      }
   }
}
