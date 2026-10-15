package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.env.PropertySourceInfo;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.PropertySource;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

class ConfigDataEnvironmentContributor implements Iterable<ConfigDataEnvironmentContributor> {
   private static final ConfigData.Options EMPTY_LOCATION_OPTIONS = ConfigData.Options.of(ConfigData.Option.IGNORE_IMPORTS);
   private final @Nullable ConfigDataLocation location;
   private final @Nullable ConfigDataResource resource;
   private final boolean fromProfileSpecificImport;
   private final @Nullable PropertySource<?> propertySource;
   private final @Nullable ConfigurationPropertySource configurationPropertySource;
   private final @Nullable ConfigDataProperties properties;
   private final ConfigData.Options configDataOptions;
   private final Map<ConfigDataEnvironmentContributor.ImportPhase, List<ConfigDataEnvironmentContributor>> children;
   private final ConfigDataEnvironmentContributor.Kind kind;
   private final ConversionService conversionService;

   ConfigDataEnvironmentContributor(
      ConfigDataEnvironmentContributor.Kind kind,
      @Nullable ConfigDataLocation location,
      @Nullable ConfigDataResource resource,
      boolean fromProfileSpecificImport,
      @Nullable PropertySource<?> propertySource,
      @Nullable ConfigurationPropertySource configurationPropertySource,
      @Nullable ConfigDataProperties properties,
      ConfigData.@Nullable Options configDataOptions,
      @Nullable Map<ConfigDataEnvironmentContributor.ImportPhase, List<ConfigDataEnvironmentContributor>> children,
      ConversionService conversionService
   ) {
      this.kind = kind;
      this.location = location;
      this.resource = resource;
      this.fromProfileSpecificImport = fromProfileSpecificImport;
      this.properties = properties;
      this.propertySource = propertySource;
      this.configurationPropertySource = configurationPropertySource;
      this.configDataOptions = configDataOptions != null ? configDataOptions : ConfigData.Options.NONE;
      this.children = children != null ? children : Collections.emptyMap();
      this.conversionService = conversionService;
   }

   ConfigDataEnvironmentContributor.Kind getKind() {
      return this.kind;
   }

   @Nullable ConfigDataLocation getLocation() {
      return this.location;
   }

   boolean isActive(@Nullable ConfigDataActivationContext activationContext) {
      return this.kind == ConfigDataEnvironmentContributor.Kind.UNBOUND_IMPORT ? false : this.properties == null || this.properties.isActive(activationContext);
   }

   @Nullable ConfigDataResource getResource() {
      return this.resource;
   }

   boolean isFromProfileSpecificImport() {
      return this.fromProfileSpecificImport;
   }

   @Nullable PropertySource<?> getPropertySource() {
      return this.propertySource;
   }

   @Nullable ConfigurationPropertySource getConfigurationPropertySource() {
      return this.configurationPropertySource;
   }

   boolean hasConfigDataOption(ConfigData.Option option) {
      return this.configDataOptions.contains(option);
   }

   ConfigDataEnvironmentContributor withoutConfigDataOption(ConfigData.Option option) {
      return new ConfigDataEnvironmentContributor(
         this.kind,
         this.location,
         this.resource,
         this.fromProfileSpecificImport,
         this.propertySource,
         this.configurationPropertySource,
         this.properties,
         this.configDataOptions.without(option),
         this.children,
         this.conversionService
      );
   }

   List<ConfigDataLocation> getImports() {
      return this.properties != null ? this.properties.getImports() : Collections.emptyList();
   }

   boolean hasUnprocessedImports(ConfigDataEnvironmentContributor.ImportPhase importPhase) {
      return this.getImports().isEmpty() ? false : !this.children.containsKey(importPhase);
   }

   List<ConfigDataEnvironmentContributor> getChildren(ConfigDataEnvironmentContributor.ImportPhase importPhase) {
      return this.children.getOrDefault(importPhase, Collections.emptyList());
   }

   Stream<ConfigDataEnvironmentContributor> stream() {
      return StreamSupport.stream(this.spliterator(), false);
   }

   @Override
   public Iterator<ConfigDataEnvironmentContributor> iterator() {
      return new ConfigDataEnvironmentContributor.ContributorIterator();
   }

   ConfigDataEnvironmentContributor withBoundProperties(
      Iterable<ConfigDataEnvironmentContributor> contributors, @Nullable ConfigDataActivationContext activationContext
   ) {
      ConfigurationPropertySource configurationPropertySource = this.getConfigurationPropertySource();
      Assert.state(configurationPropertySource != null, "'configurationPropertySource' must not be null");
      Iterable<ConfigurationPropertySource> sources = Collections.singleton(configurationPropertySource);
      PlaceholdersResolver placeholdersResolver = new ConfigDataEnvironmentContributorPlaceholdersResolver(
         contributors, activationContext, this, true, this.conversionService
      );
      Binder binder = new Binder(sources, placeholdersResolver, null, null, null);
      ConfigDataProperties properties = ConfigDataProperties.get(binder);
      if (properties != null && this.configDataOptions.contains(ConfigData.Option.IGNORE_IMPORTS)) {
         properties = properties.withoutImports();
      }

      return new ConfigDataEnvironmentContributor(
         ConfigDataEnvironmentContributor.Kind.BOUND_IMPORT,
         this.location,
         this.resource,
         this.fromProfileSpecificImport,
         this.propertySource,
         this.configurationPropertySource,
         properties,
         this.configDataOptions,
         null,
         this.conversionService
      );
   }

   ConfigDataEnvironmentContributor withChildren(ConfigDataEnvironmentContributor.ImportPhase importPhase, List<ConfigDataEnvironmentContributor> children) {
      Map<ConfigDataEnvironmentContributor.ImportPhase, List<ConfigDataEnvironmentContributor>> updatedChildren = new LinkedHashMap<>(this.children);
      updatedChildren.put(importPhase, children);
      if (importPhase == ConfigDataEnvironmentContributor.ImportPhase.AFTER_PROFILE_ACTIVATION) {
         this.moveProfileSpecific(updatedChildren);
      }

      return new ConfigDataEnvironmentContributor(
         this.kind,
         this.location,
         this.resource,
         this.fromProfileSpecificImport,
         this.propertySource,
         this.configurationPropertySource,
         this.properties,
         this.configDataOptions,
         updatedChildren,
         this.conversionService
      );
   }

   private void moveProfileSpecific(Map<ConfigDataEnvironmentContributor.ImportPhase, List<ConfigDataEnvironmentContributor>> children) {
      List<ConfigDataEnvironmentContributor> before = children.get(ConfigDataEnvironmentContributor.ImportPhase.BEFORE_PROFILE_ACTIVATION);
      if (this.hasAnyProfileSpecificChildren(before)) {
         List<ConfigDataEnvironmentContributor> updatedBefore = new ArrayList<>(before.size());
         List<ConfigDataEnvironmentContributor> updatedAfter = new ArrayList<>();

         for (ConfigDataEnvironmentContributor contributor : before) {
            updatedBefore.add(this.moveProfileSpecificChildren(contributor, updatedAfter));
         }

         updatedAfter.addAll(children.getOrDefault(ConfigDataEnvironmentContributor.ImportPhase.AFTER_PROFILE_ACTIVATION, Collections.emptyList()));
         children.put(ConfigDataEnvironmentContributor.ImportPhase.BEFORE_PROFILE_ACTIVATION, updatedBefore);
         children.put(ConfigDataEnvironmentContributor.ImportPhase.AFTER_PROFILE_ACTIVATION, updatedAfter);
      }
   }

   private ConfigDataEnvironmentContributor moveProfileSpecificChildren(
      ConfigDataEnvironmentContributor contributor, List<ConfigDataEnvironmentContributor> removed
   ) {
      for (ConfigDataEnvironmentContributor.ImportPhase importPhase : ConfigDataEnvironmentContributor.ImportPhase.values()) {
         List<ConfigDataEnvironmentContributor> children = contributor.getChildren(importPhase);
         List<ConfigDataEnvironmentContributor> updatedChildren = new ArrayList<>(children.size());

         for (ConfigDataEnvironmentContributor child : children) {
            if (child.hasConfigDataOption(ConfigData.Option.PROFILE_SPECIFIC)) {
               removed.add(child.withoutConfigDataOption(ConfigData.Option.PROFILE_SPECIFIC));
            } else {
               updatedChildren.add(child);
            }
         }

         contributor = contributor.withChildren(importPhase, updatedChildren);
      }

      return contributor;
   }

   @Contract("null -> false")
   private boolean hasAnyProfileSpecificChildren(@Nullable List<ConfigDataEnvironmentContributor> contributors) {
      if (CollectionUtils.isEmpty(contributors)) {
         return false;
      }

      for (ConfigDataEnvironmentContributor contributor : contributors) {
         for (ConfigDataEnvironmentContributor.ImportPhase importPhase : ConfigDataEnvironmentContributor.ImportPhase.values()) {
            if (contributor.getChildren(importPhase).stream().anyMatch(child -> child.hasConfigDataOption(ConfigData.Option.PROFILE_SPECIFIC))) {
               return true;
            }
         }
      }

      return false;
   }

   ConfigDataEnvironmentContributor withReplacement(ConfigDataEnvironmentContributor existing, ConfigDataEnvironmentContributor replacement) {
      if (this == existing) {
         return replacement;
      }

      Map<ConfigDataEnvironmentContributor.ImportPhase, List<ConfigDataEnvironmentContributor>> updatedChildren = new LinkedHashMap<>(this.children.size());
      this.children.forEach((importPhase, contributors) -> {
         List<ConfigDataEnvironmentContributor> updatedContributors = new ArrayList<>(contributors.size());

         for (ConfigDataEnvironmentContributor contributor : contributors) {
            updatedContributors.add(contributor.withReplacement(existing, replacement));
         }

         updatedChildren.put(importPhase, Collections.unmodifiableList(updatedContributors));
      });
      return new ConfigDataEnvironmentContributor(
         this.kind,
         this.location,
         this.resource,
         this.fromProfileSpecificImport,
         this.propertySource,
         this.configurationPropertySource,
         this.properties,
         this.configDataOptions,
         updatedChildren,
         this.conversionService
      );
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder();
      this.buildToString("", builder);
      return builder.toString();
   }

   private void buildToString(String prefix, StringBuilder builder) {
      builder.append(prefix);
      builder.append(this.kind);
      builder.append(" ");
      builder.append(this.location);
      builder.append(" ");
      builder.append(this.resource);
      builder.append(" ");
      builder.append(this.configDataOptions);
      builder.append("\n");

      for (ConfigDataEnvironmentContributor child : this.children
         .getOrDefault(ConfigDataEnvironmentContributor.ImportPhase.BEFORE_PROFILE_ACTIVATION, Collections.emptyList())) {
         child.buildToString(prefix + "    ", builder);
      }

      for (ConfigDataEnvironmentContributor child : this.children
         .getOrDefault(ConfigDataEnvironmentContributor.ImportPhase.AFTER_PROFILE_ACTIVATION, Collections.emptyList())) {
         child.buildToString(prefix + "    ", builder);
      }
   }

   static ConfigDataEnvironmentContributor of(List<ConfigDataEnvironmentContributor> contributors, ConversionService conversionService) {
      Map<ConfigDataEnvironmentContributor.ImportPhase, List<ConfigDataEnvironmentContributor>> children = new LinkedHashMap<>();
      children.put(ConfigDataEnvironmentContributor.ImportPhase.BEFORE_PROFILE_ACTIVATION, Collections.unmodifiableList(contributors));
      return new ConfigDataEnvironmentContributor(
         ConfigDataEnvironmentContributor.Kind.ROOT, null, null, false, null, null, null, null, children, conversionService
      );
   }

   static ConfigDataEnvironmentContributor ofInitialImports(List<ConfigDataLocation> initialImports, ConversionService conversionService) {
      ConfigDataProperties properties = new ConfigDataProperties(initialImports, null);
      return new ConfigDataEnvironmentContributor(
         ConfigDataEnvironmentContributor.Kind.INITIAL_IMPORT, null, null, false, null, null, properties, null, null, conversionService
      );
   }

   static ConfigDataEnvironmentContributor ofExisting(PropertySource<?> propertySource, ConversionService conversionService) {
      return new ConfigDataEnvironmentContributor(
         ConfigDataEnvironmentContributor.Kind.EXISTING,
         null,
         null,
         false,
         propertySource,
         asConfigurationPropertySource(propertySource),
         null,
         null,
         null,
         conversionService
      );
   }

   static ConfigDataEnvironmentContributor ofUnboundImport(
      @Nullable ConfigDataLocation location,
      @Nullable ConfigDataResource resource,
      boolean profileSpecific,
      ConfigData configData,
      int propertySourceIndex,
      ConversionService conversionService,
      ConfigDataEnvironmentUpdateListener environmentUpdateListener
   ) {
      PropertySource<?> propertySource = configData.getPropertySources().get(propertySourceIndex);
      ConfigData.Options options = configData.getOptions(propertySource);
      options = environmentUpdateListener.onConfigDataOptions(configData, propertySource, options);
      return new ConfigDataEnvironmentContributor(
         ConfigDataEnvironmentContributor.Kind.UNBOUND_IMPORT,
         location,
         resource,
         profileSpecific,
         propertySource,
         asConfigurationPropertySource(propertySource),
         null,
         options,
         null,
         conversionService
      );
   }

   private static @Nullable ConfigurationPropertySource asConfigurationPropertySource(PropertySource<?> propertySource) {
      ConfigurationPropertySource configurationPropertySource = ConfigurationPropertySource.from(propertySource);
      if (configurationPropertySource != null && propertySource instanceof PropertySourceInfo propertySourceInfo) {
         configurationPropertySource = configurationPropertySource.withPrefix(propertySourceInfo.getPrefix());
      }

      return configurationPropertySource;
   }

   static ConfigDataEnvironmentContributor ofEmptyLocation(ConfigDataLocation location, boolean profileSpecific, ConversionService conversionService) {
      return new ConfigDataEnvironmentContributor(
         ConfigDataEnvironmentContributor.Kind.EMPTY_LOCATION,
         location,
         null,
         profileSpecific,
         null,
         null,
         null,
         EMPTY_LOCATION_OPTIONS,
         null,
         conversionService
      );
   }

   private final class ContributorIterator implements Iterator<ConfigDataEnvironmentContributor> {
      private ConfigDataEnvironmentContributor.@Nullable ImportPhase phase = ConfigDataEnvironmentContributor.ImportPhase.AFTER_PROFILE_ACTIVATION;
      private Iterator<ConfigDataEnvironmentContributor> children = ConfigDataEnvironmentContributor.this.getChildren(this.phase).iterator();
      private Iterator<ConfigDataEnvironmentContributor> current = Collections.emptyIterator();
      private @Nullable ConfigDataEnvironmentContributor next;

      @Override
      public boolean hasNext() {
         return this.fetchIfNecessary() != null;
      }

      public ConfigDataEnvironmentContributor next() {
         ConfigDataEnvironmentContributor next = this.fetchIfNecessary();
         if (next == null) {
            throw new NoSuchElementException();
         }

         this.next = null;
         return next;
      }

      private @Nullable ConfigDataEnvironmentContributor fetchIfNecessary() {
         if (this.next != null) {
            return this.next;
         } else if (this.current.hasNext()) {
            this.next = this.current.next();
            return this.next;
         } else if (this.children.hasNext()) {
            this.current = this.children.next().iterator();
            return this.fetchIfNecessary();
         } else if (this.phase == ConfigDataEnvironmentContributor.ImportPhase.AFTER_PROFILE_ACTIVATION) {
            this.phase = ConfigDataEnvironmentContributor.ImportPhase.BEFORE_PROFILE_ACTIVATION;
            this.children = ConfigDataEnvironmentContributor.this.getChildren(this.phase).iterator();
            return this.fetchIfNecessary();
         } else if (this.phase == ConfigDataEnvironmentContributor.ImportPhase.BEFORE_PROFILE_ACTIVATION) {
            this.phase = null;
            this.next = ConfigDataEnvironmentContributor.this;
            return this.next;
         } else {
            return null;
         }
      }
   }

   enum ImportPhase {
      BEFORE_PROFILE_ACTIVATION,
      AFTER_PROFILE_ACTIVATION;

      static ConfigDataEnvironmentContributor.ImportPhase get(@Nullable ConfigDataActivationContext activationContext) {
         return activationContext != null && activationContext.getProfiles() != null ? AFTER_PROFILE_ACTIVATION : BEFORE_PROFILE_ACTIVATION;
      }
   }

   enum Kind {
      ROOT,
      INITIAL_IMPORT,
      EXISTING,
      UNBOUND_IMPORT,
      BOUND_IMPORT,
      EMPTY_LOCATION;
   }
}
