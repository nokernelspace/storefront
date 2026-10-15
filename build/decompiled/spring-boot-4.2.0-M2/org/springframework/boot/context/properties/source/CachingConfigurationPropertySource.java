package org.springframework.boot.context.properties.source;

import org.jspecify.annotations.Nullable;

interface CachingConfigurationPropertySource {
   ConfigurationPropertyCaching getCaching();

   static @Nullable ConfigurationPropertyCaching find(@Nullable ConfigurationPropertySource source) {
      return source instanceof CachingConfigurationPropertySource cachingSource ? cachingSource.getCaching() : null;
   }
}
