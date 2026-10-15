package org.springframework.boot.context.config;

import java.io.IOException;
import java.util.List;
import org.springframework.core.env.PropertySource;

class SystemEnvironmentConfigDataLoader implements ConfigDataLoader<SystemEnvironmentConfigDataResource> {
   public ConfigData load(ConfigDataLoaderContext context, SystemEnvironmentConfigDataResource resource) throws IOException, ConfigDataResourceNotFoundException {
      List<PropertySource<?>> loaded = resource.load();
      if (loaded == null) {
         throw new ConfigDataResourceNotFoundException(resource);
      } else {
         return new ConfigData(loaded);
      }
   }
}
