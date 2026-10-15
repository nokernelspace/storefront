package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

class ConfigDataEnvironment {
   static final String LOCATION_PROPERTY = "spring.config.location";
   static final String ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";
   static final String IMPORT_PROPERTY = "spring.config.import";
   static final String ON_NOT_FOUND_PROPERTY = "spring.config.on-not-found";
   static final ConfigDataLocation[] DEFAULT_SEARCH_LOCATIONS;
   private static final ConfigDataLocation[] EMPTY_LOCATIONS = new ConfigDataLocation[0];
   private static final Bindable<ConfigDataLocation[]> CONFIG_DATA_LOCATION_ARRAY = Bindable.of(ConfigDataLocation[].class);
   private static final Bindable<List<String>> STRING_LIST = Bindable.listOf(String.class);
   private static final ConfigDataEnvironmentContributors.BinderOption[] ALLOW_INACTIVE_BINDING = new ConfigDataEnvironmentContributors.BinderOption[0];
   private static final ConfigDataEnvironmentContributors.BinderOption[] DENY_INACTIVE_BINDING = new ConfigDataEnvironmentContributors.BinderOption[]{
      ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE
   };
   private final DeferredLogFactory logFactory;
   private final Log logger;
   private final ConfigDataNotFoundAction notFoundAction;
   private final ConfigurableBootstrapContext bootstrapContext;
   private final ConfigurableEnvironment environment;
   private final ConfigDataLocationResolvers resolvers;
   private final Collection<String> additionalProfiles;
   private final ConfigDataEnvironmentUpdateListener environmentUpdateListener;
   private final ConfigDataLoaders loaders;
   private final ConfigDataEnvironmentContributors contributors;

   ConfigDataEnvironment(
      DeferredLogFactory logFactory,
      ConfigurableBootstrapContext bootstrapContext,
      ConfigurableEnvironment environment,
      ResourceLoader resourceLoader,
      Collection<String> additionalProfiles,
      @Nullable ConfigDataEnvironmentUpdateListener environmentUpdateListener
   ) {
      Binder binder = Binder.get(environment);
      this.logFactory = logFactory;
      this.logger = logFactory.getLog(this.getClass());
      this.notFoundAction = binder.bind("spring.config.on-not-found", ConfigDataNotFoundAction.class).orElse(ConfigDataNotFoundAction.FAIL);
      this.bootstrapContext = bootstrapContext;
      this.environment = environment;
      this.resolvers = this.createConfigDataLocationResolvers(logFactory, bootstrapContext, binder, resourceLoader);
      this.additionalProfiles = additionalProfiles;
      this.environmentUpdateListener = environmentUpdateListener != null ? environmentUpdateListener : ConfigDataEnvironmentUpdateListener.NONE;
      this.loaders = new ConfigDataLoaders(logFactory, bootstrapContext, SpringFactoriesLoader.forDefaultResourceLocation(resourceLoader.getClassLoader()));
      this.contributors = this.createContributors(binder);
   }

   protected ConfigDataLocationResolvers createConfigDataLocationResolvers(
      DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext, Binder binder, ResourceLoader resourceLoader
   ) {
      return new ConfigDataLocationResolvers(
         logFactory, bootstrapContext, binder, resourceLoader, SpringFactoriesLoader.forDefaultResourceLocation(resourceLoader.getClassLoader())
      );
   }

   private ConfigDataEnvironmentContributors createContributors(Binder binder) {
      this.logger.trace("Building config data environment contributors");
      MutablePropertySources propertySources = this.environment.getPropertySources();
      List<ConfigDataEnvironmentContributor> contributors = new ArrayList<>(propertySources.size() + 10);
      PropertySource<?> defaultPropertySource = null;

      for (PropertySource<?> propertySource : propertySources) {
         if (DefaultPropertiesPropertySource.hasMatchingName(propertySource)) {
            defaultPropertySource = propertySource;
         } else {
            this.logger.trace(LogMessage.format("Creating wrapped config data contributor for '%s'", propertySource.getName()));
            contributors.add(ConfigDataEnvironmentContributor.ofExisting(propertySource, this.environment.getConversionService()));
         }
      }

      contributors.addAll(this.getInitialImportContributors(binder));
      if (defaultPropertySource != null) {
         this.logger.trace("Creating wrapped config data contributor for default property source");
         contributors.add(ConfigDataEnvironmentContributor.ofExisting(defaultPropertySource, this.environment.getConversionService()));
      }

      return this.createContributors(contributors);
   }

   protected ConfigDataEnvironmentContributors createContributors(List<ConfigDataEnvironmentContributor> contributors) {
      return new ConfigDataEnvironmentContributors(
         this.logFactory, this.bootstrapContext, contributors, this.environment.getConversionService(), this.environmentUpdateListener
      );
   }

   ConfigDataEnvironmentContributors getContributors() {
      return this.contributors;
   }

   private List<ConfigDataEnvironmentContributor> getInitialImportContributors(Binder binder) {
      List<ConfigDataEnvironmentContributor> initialContributors = new ArrayList<>();
      this.addInitialImportContributors(initialContributors, binder, "spring.config.import", EMPTY_LOCATIONS, false);
      this.addInitialImportContributors(initialContributors, binder, "spring.config.additional-location", EMPTY_LOCATIONS, true);
      this.addInitialImportContributors(initialContributors, binder, "spring.config.location", DEFAULT_SEARCH_LOCATIONS, true);
      return initialContributors;
   }

   private void addInitialImportContributors(
      List<ConfigDataEnvironmentContributor> initialContributors,
      Binder binder,
      String propertyName,
      ConfigDataLocation[] defaultValue,
      boolean registerIndividually
   ) {
      ConfigDataLocation[] locations = binder.bind(propertyName, CONFIG_DATA_LOCATION_ARRAY).orElse(defaultValue);
      if (registerIndividually) {
         for (int i = locations.length - 1; i >= 0; i--) {
            this.addInitialImportContributors(initialContributors, List.of(locations[i]));
         }
      } else {
         this.addInitialImportContributors(initialContributors, List.of(locations));
      }
   }

   private void addInitialImportContributors(List<ConfigDataEnvironmentContributor> initialContributors, List<ConfigDataLocation> locations) {
      if (!locations.isEmpty()) {
         this.logger.trace(LogMessage.format("Adding initial config data import from locations %s", locations));
         ConfigDataEnvironmentContributor contributor = ConfigDataEnvironmentContributor.ofInitialImports(locations, this.environment.getConversionService());
         initialContributors.add(contributor);
      }
   }

   void processAndApply() {
      ConfigDataImporter importer = new ConfigDataImporter(this.logFactory, this.notFoundAction, this.resolvers, this.loaders);
      this.registerBootstrapBinder(this.contributors, null, DENY_INACTIVE_BINDING);
      ConfigDataEnvironmentContributors contributors = this.processInitial(this.contributors, importer);
      ConfigDataActivationContext activationContext = this.createActivationContext(
         contributors.getBinder(null, ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE)
      );
      contributors = this.processWithoutProfiles(contributors, importer, activationContext);
      activationContext = this.withProfiles(contributors, activationContext);
      contributors = this.processWithProfiles(contributors, importer, activationContext);
      this.applyToEnvironment(contributors, activationContext, importer.getLoadedLocations(), importer.getOptionalLocations());
   }

   private ConfigDataEnvironmentContributors processInitial(ConfigDataEnvironmentContributors contributors, ConfigDataImporter importer) {
      this.logger.trace("Processing initial config data environment contributors without activation context");
      contributors = contributors.withProcessedImports(importer, null);
      this.registerBootstrapBinder(contributors, null, DENY_INACTIVE_BINDING);
      return contributors;
   }

   private ConfigDataActivationContext createActivationContext(Binder initialBinder) {
      this.logger.trace("Creating config data activation context from initial contributions");

      try {
         return new ConfigDataActivationContext(this.environment, initialBinder);
      } catch (BindException ex) {
         if (ex.getCause() instanceof InactiveConfigDataAccessException inactiveException) {
            throw inactiveException;
         } else {
            throw ex;
         }
      }
   }

   private ConfigDataEnvironmentContributors processWithoutProfiles(
      ConfigDataEnvironmentContributors contributors, ConfigDataImporter importer, ConfigDataActivationContext activationContext
   ) {
      this.logger.trace("Processing config data environment contributors with initial activation context");
      contributors = contributors.withProcessedImports(importer, activationContext);
      this.registerBootstrapBinder(contributors, activationContext, DENY_INACTIVE_BINDING);
      return contributors;
   }

   private ConfigDataActivationContext withProfiles(ConfigDataEnvironmentContributors contributors, ConfigDataActivationContext activationContext) {
      this.logger.trace("Deducing profiles from current config data environment contributors");
      Binder binder = contributors.getBinder(
         activationContext,
         contributor -> !contributor.hasConfigDataOption(ConfigData.Option.IGNORE_PROFILES),
         ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE
      );

      try {
         Set<String> additionalProfiles = new LinkedHashSet<>(this.additionalProfiles);
         additionalProfiles.addAll(this.getIncludedProfiles(contributors, activationContext));
         Profiles profiles = new Profiles(this.environment, binder, additionalProfiles);
         return activationContext.withProfiles(profiles);
      } catch (BindException ex) {
         if (ex.getCause() instanceof InactiveConfigDataAccessException inactiveException) {
            throw inactiveException;
         } else {
            throw ex;
         }
      }
   }

   private Collection<? extends String> getIncludedProfiles(ConfigDataEnvironmentContributors contributors, ConfigDataActivationContext activationContext) {
      PlaceholdersResolver placeholdersResolver = new ConfigDataEnvironmentContributorPlaceholdersResolver(
         contributors, activationContext, null, true, this.environment.getConversionService()
      );
      Set<String> result = new LinkedHashSet<>();

      for (ConfigDataEnvironmentContributor contributor : contributors) {
         ConfigurationPropertySource source = contributor.getConfigurationPropertySource();
         if (source != null && !contributor.hasConfigDataOption(ConfigData.Option.IGNORE_PROFILES)) {
            Binder binder = new Binder(Collections.singleton(source), placeholdersResolver);
            binder.bind(Profiles.INCLUDE_PROFILES, STRING_LIST, ProfilesValidator.get(binder)).ifBound(includes -> {
               if (!contributor.isActive(activationContext)) {
                  InactiveConfigDataAccessException.throwIfPropertyFound(contributor, Profiles.INCLUDE_PROFILES);
                  InactiveConfigDataAccessException.throwIfPropertyFound(contributor, Profiles.INCLUDE_PROFILES.append("[0]"));
               }

               result.addAll((Collection<? extends String>)includes);
            });
         }
      }

      return result;
   }

   private ConfigDataEnvironmentContributors processWithProfiles(
      ConfigDataEnvironmentContributors contributors, ConfigDataImporter importer, ConfigDataActivationContext activationContext
   ) {
      this.logger.trace("Processing config data environment contributors with profile activation context");
      contributors = contributors.withProcessedImports(importer, activationContext);
      this.registerBootstrapBinder(contributors, activationContext, ALLOW_INACTIVE_BINDING);
      return contributors;
   }

   private void registerBootstrapBinder(
      ConfigDataEnvironmentContributors contributors,
      @Nullable ConfigDataActivationContext activationContext,
      ConfigDataEnvironmentContributors.BinderOption... binderOptions
   ) {
      this.bootstrapContext
         .register(
            Binder.class,
            BootstrapRegistry.InstanceSupplier.<Binder>from(() -> contributors.getBinder(activationContext, binderOptions))
               .withScope(BootstrapRegistry.Scope.PROTOTYPE)
         );
   }

   private void applyToEnvironment(
      ConfigDataEnvironmentContributors contributors,
      ConfigDataActivationContext activationContext,
      Set<ConfigDataLocation> loadedLocations,
      Set<ConfigDataLocation> optionalLocations
   ) {
      this.checkForInvalidProperties(contributors);
      this.checkMandatoryLocations(contributors, activationContext, loadedLocations, optionalLocations);
      MutablePropertySources propertySources = this.environment.getPropertySources();
      this.applyContributor(contributors, activationContext, propertySources);
      DefaultPropertiesPropertySource.moveToEnd(propertySources);
      Profiles profiles = activationContext.getProfiles();
      Assert.state(profiles != null, "'profiles' must not be null");
      this.logger.trace(LogMessage.format("Setting default profiles: %s", profiles.getDefault()));
      this.environment.setDefaultProfiles(StringUtils.toStringArray(profiles.getDefault()));
      this.logger.trace(LogMessage.format("Setting active profiles: %s", profiles.getActive()));
      this.environment.setActiveProfiles(StringUtils.toStringArray(profiles.getActive()));
      this.environmentUpdateListener.onSetProfiles(profiles);
   }

   private void applyContributor(
      ConfigDataEnvironmentContributors contributors, ConfigDataActivationContext activationContext, MutablePropertySources propertySources
   ) {
      this.logger.trace("Applying config data environment contributions");

      for (ConfigDataEnvironmentContributor contributor : contributors) {
         PropertySource<?> propertySource = contributor.getPropertySource();
         if (contributor.getKind() == ConfigDataEnvironmentContributor.Kind.BOUND_IMPORT && propertySource != null) {
            if (!contributor.isActive(activationContext)) {
               this.logger.trace(LogMessage.format("Skipping inactive property source '%s'", propertySource.getName()));
            } else {
               this.logger.trace(LogMessage.format("Adding imported property source '%s'", propertySource.getName()));
               propertySources.addLast(propertySource);
               this.environmentUpdateListener.onPropertySourceAdded(propertySource, contributor.getLocation(), contributor.getResource());
            }
         }
      }
   }

   private void checkForInvalidProperties(ConfigDataEnvironmentContributors contributors) {
      for (ConfigDataEnvironmentContributor contributor : contributors) {
         InvalidConfigDataPropertyException.throwIfPropertyFound(contributor);
      }
   }

   private void checkMandatoryLocations(
      ConfigDataEnvironmentContributors contributors,
      ConfigDataActivationContext activationContext,
      Set<ConfigDataLocation> loadedLocations,
      Set<ConfigDataLocation> optionalLocations
   ) {
      Set<ConfigDataLocation> mandatoryLocations = new LinkedHashSet<>();

      for (ConfigDataEnvironmentContributor contributor : contributors) {
         if (contributor.isActive(activationContext)) {
            mandatoryLocations.addAll(this.getMandatoryImports(contributor));
         }
      }

      for (ConfigDataEnvironmentContributor contributor : contributors) {
         if (contributor.getLocation() != null) {
            mandatoryLocations.remove(contributor.getLocation());
         }
      }

      mandatoryLocations.removeAll(loadedLocations);
      mandatoryLocations.removeAll(optionalLocations);
      if (!mandatoryLocations.isEmpty()) {
         for (ConfigDataLocation mandatoryLocation : mandatoryLocations) {
            this.notFoundAction.handle(this.logger, new ConfigDataLocationNotFoundException(mandatoryLocation));
         }
      }
   }

   private Set<ConfigDataLocation> getMandatoryImports(ConfigDataEnvironmentContributor contributor) {
      List<ConfigDataLocation> imports = contributor.getImports();
      Set<ConfigDataLocation> mandatoryLocations = new LinkedHashSet<>(imports.size());

      for (ConfigDataLocation location : imports) {
         if (!location.isOptional()) {
            mandatoryLocations.add(location);
         }
      }

      return mandatoryLocations;
   }

   static {
      List<ConfigDataLocation> locations = new ArrayList<>();
      locations.add(ConfigDataLocation.of("optional:classpath:/;optional:classpath:/config/"));
      locations.add(ConfigDataLocation.of("optional:file:./;optional:file:./config/;optional:file:./config/*/"));
      DEFAULT_SEARCH_LOCATIONS = locations.toArray(new ConfigDataLocation[0]);
   }
}
