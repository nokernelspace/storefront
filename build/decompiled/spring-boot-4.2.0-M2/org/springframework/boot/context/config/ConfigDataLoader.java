package org.springframework.boot.context.config;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

public interface ConfigDataLoader<R extends ConfigDataResource> {
   default boolean isLoadable(ConfigDataLoaderContext context, R resource) {
      return true;
   }

   @Nullable ConfigData load(ConfigDataLoaderContext context, R resource) throws IOException, ConfigDataResourceNotFoundException;
}
