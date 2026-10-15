package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.util.ObjectUtils;

class ConfigDataEnvironmentContributors implements Iterable<ConfigDataEnvironmentContributor> {
   private static final Predicate<ConfigDataEnvironmentContributor> NO_CONTRIBUTOR_FILTER = contributor -> true;
   private final Log logger;
   private final ConfigDataEnvironmentContributor root;
   private final ConfigurableBootstrapContext bootstrapContext;
   private final ConversionService conversionService;
   private final ConfigDataEnvironmentUpdateListener environmentUpdateListener;

   ConfigDataEnvironmentContributors(
      DeferredLogFactory logFactory,
      ConfigurableBootstrapContext bootstrapContext,
      List<ConfigDataEnvironmentContributor> contributors,
      ConversionService conversionService,
      ConfigDataEnvironmentUpdateListener environmentUpdateListener
   ) {
      this.logger = logFactory.getLog(this.getClass());
      this.bootstrapContext = bootstrapContext;
      this.root = ConfigDataEnvironmentContributor.of(contributors, conversionService);
      this.conversionService = conversionService;
      this.environmentUpdateListener = environmentUpdateListener;
   }

   private ConfigDataEnvironmentContributors(
      Log logger,
      ConfigurableBootstrapContext bootstrapContext,
      ConfigDataEnvironmentContributor root,
      ConversionService conversionService,
      ConfigDataEnvironmentUpdateListener environmentUpdateListener
   ) {
      this.logger = logger;
      this.bootstrapContext = bootstrapContext;
      this.root = root;
      this.conversionService = conversionService;
      this.environmentUpdateListener = environmentUpdateListener;
   }

   ConfigDataEnvironmentContributors withProcessedImports(ConfigDataImporter importer, @Nullable ConfigDataActivationContext activationContext) {
      ConfigDataEnvironmentContributor.ImportPhase importPhase = ConfigDataEnvironmentContributor.ImportPhase.get(activationContext);
      this.logger
         .trace(LogMessage.format("Processing imports for phase %s. %s", importPhase, activationContext != null ? activationContext : "no activation context"));
      ConfigDataEnvironmentContributors result = this;
      int processed = 0;

      while (true) {
         ConfigDataEnvironmentContributor contributor = this.getNextToProcess(result, activationContext, importPhase);
         if (contributor == null) {
            this.logger.trace(LogMessage.format("Processed imports for of %d contributors", processed));
            return result;
         }

         if (contributor.getKind() == ConfigDataEnvironmentContributor.Kind.UNBOUND_IMPORT) {
            ConfigDataEnvironmentContributor bound = contributor.withBoundProperties(result, activationContext);
            result = new ConfigDataEnvironmentContributors(
               this.logger, this.bootstrapContext, result.getRoot().withReplacement(contributor, bound), this.conversionService, this.environmentUpdateListener
            );
         } else {
            ConfigDataLocationResolverContext locationResolverContext = new ConfigDataEnvironmentContributors.ContributorConfigDataLocationResolverContext(
               result, contributor, activationContext
            );
            ConfigDataLoaderContext loaderContext = new ConfigDataEnvironmentContributors.ContributorDataLoaderContext(this);
            List<ConfigDataLocation> imports = contributor.getImports();
            this.logger.trace(LogMessage.format("Processing imports %s", imports));
            Map<ConfigDataResolutionResult, ConfigData> imported = importer.resolveAndLoad(activationContext, locationResolverContext, loaderContext, imports);
            this.logger.trace(LogMessage.of(() -> this.getImportedMessage(imported.keySet())));
            ConfigDataEnvironmentContributor contributorAndChildren = contributor.withChildren(importPhase, this.asContributors(imported));
            result = new ConfigDataEnvironmentContributors(
               this.logger,
               this.bootstrapContext,
               result.getRoot().withReplacement(contributor, contributorAndChildren),
               this.conversionService,
               this.environmentUpdateListener
            );
            processed++;
         }
      }
   }

   private CharSequence getImportedMessage(Set<ConfigDataResolutionResult> results) {
      if (results.isEmpty()) {
         return "Nothing imported";
      }

      StringBuilder message = new StringBuilder();
      message.append("Imported " + results.size() + " resource" + (results.size() != 1 ? "s " : " "));
      message.append(results.stream().map(ConfigDataResolutionResult::getResource).toList());
      return message;
   }

   protected final ConfigurableBootstrapContext getBootstrapContext() {
      return this.bootstrapContext;
   }

   private @Nullable ConfigDataEnvironmentContributor getNextToProcess(
      ConfigDataEnvironmentContributors contributors,
      @Nullable ConfigDataActivationContext activationContext,
      ConfigDataEnvironmentContributor.ImportPhase importPhase
   ) {
      for (ConfigDataEnvironmentContributor contributor : contributors.getRoot()) {
         if (contributor.getKind() == ConfigDataEnvironmentContributor.Kind.UNBOUND_IMPORT
            || this.isActiveWithUnprocessedImports(activationContext, importPhase, contributor)) {
            return contributor;
         }
      }

      return null;
   }

   private boolean isActiveWithUnprocessedImports(
      @Nullable ConfigDataActivationContext activationContext,
      ConfigDataEnvironmentContributor.ImportPhase importPhase,
      ConfigDataEnvironmentContributor contributor
   ) {
      return contributor.isActive(activationContext) && contributor.hasUnprocessedImports(importPhase);
   }

   private List<ConfigDataEnvironmentContributor> asContributors(Map<ConfigDataResolutionResult, ConfigData> imported) {
      List<ConfigDataEnvironmentContributor> contributors = new ArrayList<>(imported.size() * 5);
      imported.forEach(
         (resolutionResult, data) -> {
            ConfigDataLocation location = resolutionResult.getLocation();
            ConfigDataResource resource = resolutionResult.getResource();
            boolean profileSpecific = resolutionResult.isProfileSpecific();
            if (data.getPropertySources().isEmpty()) {
               contributors.add(ConfigDataEnvironmentContributor.ofEmptyLocation(location, profileSpecific, this.conversionService));
            } else {
               for (int i = data.getPropertySources().size() - 1; i >= 0; i--) {
                  contributors.add(
                     ConfigDataEnvironmentContributor.ofUnboundImport(
                        location, resource, profileSpecific, data, i, this.conversionService, this.environmentUpdateListener
                     )
                  );
               }
            }
         }
      );
      return Collections.unmodifiableList(contributors);
   }

   ConfigDataEnvironmentContributor getRoot() {
      return this.root;
   }

   Binder getBinder(@Nullable ConfigDataActivationContext activationContext, ConfigDataEnvironmentContributors.BinderOption... options) {
      return this.getBinder(activationContext, NO_CONTRIBUTOR_FILTER, options);
   }

   Binder getBinder(
      @Nullable ConfigDataActivationContext activationContext,
      Predicate<ConfigDataEnvironmentContributor> filter,
      ConfigDataEnvironmentContributors.BinderOption... options
   ) {
      return this.getBinder(activationContext, filter, this.asBinderOptionsSet(options));
   }

   private Set<ConfigDataEnvironmentContributors.BinderOption> asBinderOptionsSet(ConfigDataEnvironmentContributors.BinderOption... options) {
      return ObjectUtils.isEmpty(options) ? EnumSet.noneOf(ConfigDataEnvironmentContributors.BinderOption.class) : EnumSet.copyOf(Arrays.asList(options));
   }

   private Binder getBinder(
      @Nullable ConfigDataActivationContext activationContext,
      Predicate<ConfigDataEnvironmentContributor> filter,
      Set<ConfigDataEnvironmentContributors.BinderOption> options
   ) {
      boolean failOnInactiveSource = options.contains(ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE);
      Iterable<ConfigurationPropertySource> sources = () -> this.getBinderSources(
         filter.and(contributor -> failOnInactiveSource || contributor.isActive(activationContext))
      );
      PlaceholdersResolver placeholdersResolver = new ConfigDataEnvironmentContributorPlaceholdersResolver(
         this.root, activationContext, null, failOnInactiveSource, this.conversionService
      );
      BindHandler bindHandler = !failOnInactiveSource ? null : new ConfigDataEnvironmentContributors.InactiveSourceChecker(activationContext);
      return new Binder(sources, placeholdersResolver, null, null, bindHandler);
   }

   private Iterator<ConfigurationPropertySource> getBinderSources(Predicate<ConfigDataEnvironmentContributor> filter) {
      return this.root
         .stream()
         .filter(this::hasConfigurationPropertySource)
         .filter(filter)
         .map(ConfigDataEnvironmentContributor::getConfigurationPropertySource)
         .iterator();
   }

   private boolean hasConfigurationPropertySource(ConfigDataEnvironmentContributor contributor) {
      return contributor.getConfigurationPropertySource() != null;
   }

   @Override
   public Iterator<ConfigDataEnvironmentContributor> iterator() {
      return this.root.iterator();
   }

   enum BinderOption {
      FAIL_ON_BIND_TO_INACTIVE_SOURCE;
   }

   private static class ContributorConfigDataLocationResolverContext implements ConfigDataLocationResolverContext {
      private final ConfigDataEnvironmentContributors contributors;
      private final ConfigDataEnvironmentContributor contributor;
      private final @Nullable ConfigDataActivationContext activationContext;
      private volatile @Nullable Binder binder;

      ContributorConfigDataLocationResolverContext(
         ConfigDataEnvironmentContributors contributors, ConfigDataEnvironmentContributor contributor, @Nullable ConfigDataActivationContext activationContext
      ) {
         this.contributors = contributors;
         this.contributor = contributor;
         this.activationContext = activationContext;
      }

      @Override
      public Binder getBinder() {
         Binder binder = this.binder;
         if (binder == null) {
            binder = this.contributors.getBinder(this.activationContext);
            this.binder = binder;
         }

         return binder;
      }

      @Override
      public @Nullable ConfigDataResource getParent() {
         return this.contributor.getResource();
      }

      @Override
      public ConfigurableBootstrapContext getBootstrapContext() {
         return this.contributors.getBootstrapContext();
      }
   }

   private static class ContributorDataLoaderContext implements ConfigDataLoaderContext {
      private final ConfigDataEnvironmentContributors contributors;

      ContributorDataLoaderContext(ConfigDataEnvironmentContributors contributors) {
         this.contributors = contributors;
      }

      @Override
      public ConfigurableBootstrapContext getBootstrapContext() {
         return this.contributors.getBootstrapContext();
      }
   }

   private class InactiveSourceChecker implements BindHandler {
      private final @Nullable ConfigDataActivationContext activationContext;

      InactiveSourceChecker(@Nullable ConfigDataActivationContext activationContext) {
         this.activationContext = activationContext;
      }

      @Override
      public Object onSuccess(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) {
         for (ConfigDataEnvironmentContributor contributor : ConfigDataEnvironmentContributors.this) {
            if (!contributor.isActive(this.activationContext)) {
               InactiveConfigDataAccessException.throwIfPropertyFound(contributor, name);
            }
         }

         return result;
      }
   }
}
