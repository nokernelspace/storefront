package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.bootstrap.BootstrapContext;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;

class ConfigDataLocationResolvers {
   private final List<ConfigDataLocationResolver<?>> resolvers;

   ConfigDataLocationResolvers(
      DeferredLogFactory logFactory,
      ConfigurableBootstrapContext bootstrapContext,
      Binder binder,
      ResourceLoader resourceLoader,
      SpringFactoriesLoader springFactoriesLoader
   ) {
      ArgumentResolver argumentResolver = ArgumentResolver.of(DeferredLogFactory.class, logFactory);
      argumentResolver = argumentResolver.and(Binder.class, binder);
      argumentResolver = argumentResolver.and(ResourceLoader.class, resourceLoader);
      argumentResolver = argumentResolver.and(ConfigurableBootstrapContext.class, bootstrapContext);
      argumentResolver = argumentResolver.and(BootstrapContext.class, bootstrapContext);
      argumentResolver = argumentResolver.and(BootstrapRegistry.class, bootstrapContext);
      argumentResolver = argumentResolver.andSupplied(Log.class, () -> {
         throw new IllegalArgumentException("Log types cannot be injected, please use DeferredLogFactory");
      });
      this.resolvers = this.reorder(springFactoriesLoader.load(ConfigDataLocationResolver.class, argumentResolver));
   }

   private List<ConfigDataLocationResolver<?>> reorder(List<ConfigDataLocationResolver> resolvers) {
      List<ConfigDataLocationResolver<?>> reordered = new ArrayList<>(resolvers.size());
      ConfigDataLocationResolver<?> standardConfigDataLocationResolver = null;

      for (ConfigDataLocationResolver<?> resolver : resolvers) {
         if (resolver instanceof StandardConfigDataLocationResolver) {
            standardConfigDataLocationResolver = resolver;
         } else {
            reordered.add(resolver);
         }
      }

      if (standardConfigDataLocationResolver != null) {
         reordered.add(standardConfigDataLocationResolver);
      }

      return Collections.unmodifiableList(reordered);
   }

   List<ConfigDataResolutionResult> resolve(ConfigDataLocationResolverContext context, @Nullable ConfigDataLocation location, @Nullable Profiles profiles) {
      if (location == null) {
         return Collections.emptyList();
      }

      for (ConfigDataLocationResolver<?> resolver : this.getResolvers()) {
         if (resolver.isResolvable(context, location)) {
            return this.resolve(resolver, context, location, profiles);
         }
      }

      throw new UnsupportedConfigDataLocationException(location);
   }

   private List<ConfigDataResolutionResult> resolve(
      ConfigDataLocationResolver<?> resolver, ConfigDataLocationResolverContext context, ConfigDataLocation location, @Nullable Profiles profiles
   ) {
      List<ConfigDataResolutionResult> resolved = this.resolve(location, false, () -> (List<? extends ConfigDataResource>)resolver.resolve(context, location));
      if (profiles == null) {
         return resolved;
      }

      List<ConfigDataResolutionResult> profileSpecific = this.resolve(
         location, true, () -> (List<? extends ConfigDataResource>)resolver.resolveProfileSpecific(context, location, profiles)
      );
      return this.merge(resolved, profileSpecific);
   }

   private List<ConfigDataResolutionResult> resolve(
      ConfigDataLocation location, boolean profileSpecific, Supplier<List<? extends ConfigDataResource>> resolveAction
   ) {
      List<ConfigDataResource> resources = this.nonNullList(resolveAction.get());
      List<ConfigDataResolutionResult> resolved = new ArrayList<>(resources.size());

      for (ConfigDataResource resource : resources) {
         resolved.add(new ConfigDataResolutionResult(location, resource, profileSpecific));
      }

      return resolved;
   }

   private <T> List<T> nonNullList(@Nullable List<? extends T> list) {
      return (List<T>)(list != null ? list : Collections.emptyList());
   }

   private <T> List<T> merge(List<T> list1, List<T> list2) {
      List<T> merged = new ArrayList<>(list1.size() + list2.size());
      merged.addAll(list1);
      merged.addAll(list2);
      return merged;
   }

   List<ConfigDataLocationResolver<?>> getResolvers() {
      return this.resolvers;
   }
}
