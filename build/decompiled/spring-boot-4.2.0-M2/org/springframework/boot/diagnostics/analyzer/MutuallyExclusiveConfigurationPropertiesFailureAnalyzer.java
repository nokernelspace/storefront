package org.springframework.boot.diagnostics.analyzer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MutuallyExclusiveConfigurationPropertiesException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

class MutuallyExclusiveConfigurationPropertiesFailureAnalyzer extends AbstractFailureAnalyzer<MutuallyExclusiveConfigurationPropertiesException> {
   private final @Nullable ConfigurableEnvironment environment;

   MutuallyExclusiveConfigurationPropertiesFailureAnalyzer(@Nullable Environment environment) {
      this.environment = (ConfigurableEnvironment)environment;
   }

   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, MutuallyExclusiveConfigurationPropertiesException cause) {
      List<MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor> descriptors = new ArrayList<>();

      for (String name : cause.getConfiguredNames()) {
         List<MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor> descriptorsForName = this.getDescriptors(name);
         if (descriptorsForName.isEmpty()) {
            return null;
         }

         descriptors.addAll(descriptorsForName);
      }

      StringBuilder description = new StringBuilder();
      this.appendDetails(description, cause, descriptors);
      return new FailureAnalysis(
         description.toString(), "Update your configuration so that only one of the mutually exclusive properties is configured.", cause
      );
   }

   private List<MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor> getDescriptors(String propertyName) {
      return this.getPropertySources()
         .filter(source -> source.containsProperty(propertyName))
         .map(source -> MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor.get((PropertySource<?>)source, propertyName))
         .toList();
   }

   private Stream<PropertySource<?>> getPropertySources() {
      return this.environment == null
         ? Stream.empty()
         : this.environment
            .getPropertySources()
            .stream()
            .filter(source -> !ConfigurationPropertySources.isAttachedConfigurationPropertySource((PropertySource<?>)source));
   }

   private void appendDetails(
      StringBuilder message,
      MutuallyExclusiveConfigurationPropertiesException cause,
      List<MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor> descriptors
   ) {
      descriptors.sort(Comparator.comparing(descriptor -> descriptor.propertyName));
      message.append(String.format("The following configuration properties are mutually exclusive:%n%n"));
      this.sortedStrings(cause.getMutuallyExclusiveNames()).forEach(name -> message.append(String.format("\t%s%n", name)));
      message.append(String.format("%n"));
      message.append(String.format("However, more than one of those properties has been configured at the same time:%n%n"));
      Set<String> configuredDescriptions = this.sortedStrings(
         descriptors,
         descriptor -> String.format("\t%s%s%n", descriptor.propertyName, descriptor.origin != null ? " (originating from '" + descriptor.origin + "')" : "")
      );
      configuredDescriptions.forEach(message::append);
   }

   private Set<String> sortedStrings(Collection<String> input) {
      return this.sortedStrings(input, Function.identity());
   }

   private <S> Set<String> sortedStrings(Collection<S> input, Function<S, String> converter) {
      TreeSet<String> results = new TreeSet<>();

      for (S item : input) {
         results.add(converter.apply(item));
      }

      return results;
   }

   private static final class Descriptor {
      private final String propertyName;
      private final @Nullable Origin origin;

      private Descriptor(String propertyName, @Nullable Origin origin) {
         this.propertyName = propertyName;
         this.origin = origin;
      }

      static MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor get(PropertySource<?> source, String propertyName) {
         Origin origin = OriginLookup.getOrigin(source, propertyName);
         return new MutuallyExclusiveConfigurationPropertiesFailureAnalyzer.Descriptor(propertyName, origin);
      }
   }
}
