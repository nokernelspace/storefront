package org.springframework.boot.context.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.bootstrap.BootstrapContext;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;

class ConfigDataLoaders {
   private final Log logger;
   private final List<ConfigDataLoader> loaders;
   private final List<Class<?>> resourceTypes;

   ConfigDataLoaders(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext, SpringFactoriesLoader springFactoriesLoader) {
      this.logger = logFactory.getLog(this.getClass());
      ArgumentResolver argumentResolver = ArgumentResolver.of(DeferredLogFactory.class, logFactory);
      argumentResolver = argumentResolver.and(ConfigurableBootstrapContext.class, bootstrapContext);
      argumentResolver = argumentResolver.and(BootstrapContext.class, bootstrapContext);
      argumentResolver = argumentResolver.and(BootstrapRegistry.class, bootstrapContext);
      argumentResolver = argumentResolver.andSupplied(Log.class, () -> {
         throw new IllegalArgumentException("Log types cannot be injected, please use DeferredLogFactory");
      });
      this.loaders = springFactoriesLoader.load(ConfigDataLoader.class, argumentResolver);
      this.resourceTypes = this.getResourceTypes(this.loaders);
   }

   private List<Class<?>> getResourceTypes(List<ConfigDataLoader> loaders) {
      List<Class<?>> resourceTypes = new ArrayList<>(loaders.size());

      for (ConfigDataLoader<?> loader : loaders) {
         resourceTypes.add(this.getResourceType(loader));
      }

      return Collections.unmodifiableList(resourceTypes);
   }

   private Class<?> getResourceType(ConfigDataLoader<?> loader) {
      Class<?> generic = ResolvableType.forClass(loader.getClass()).as(ConfigDataLoader.class).resolveGeneric(new int[0]);
      Assert.state(generic != null, "'generic' must not be null");
      return generic;
   }

   <R extends ConfigDataResource> @Nullable ConfigData load(ConfigDataLoaderContext context, R resource) throws IOException {
      ConfigDataLoader<R> loader = this.getLoader(context, resource);
      this.logger.trace(LogMessage.of(() -> "Loading " + resource + " using loader " + loader.getClass().getName()));
      return loader.load(context, resource);
   }

   private <R extends ConfigDataResource> ConfigDataLoader<R> getLoader(ConfigDataLoaderContext context, R resource) {
      ConfigDataLoader<R> result = null;

      for (int i = 0; i < this.loaders.size(); i++) {
         ConfigDataLoader<R> candidate = this.loaders.get(i);
         if (this.resourceTypes.get(i).isInstance(resource) && candidate.isLoadable(context, resource)) {
            if (result != null) {
               throw new IllegalStateException(
                  "Multiple loaders found for resource '" + resource + "' [" + candidate.getClass().getName() + "," + result.getClass().getName() + "]"
               );
            }

            result = candidate;
         }
      }

      Assert.state(result != null, () -> "No loader found for resource '" + resource + "'");
      return result;
   }
}
