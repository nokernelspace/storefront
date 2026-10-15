package org.springframework.boot.context.properties.source;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

public interface ConfigurationPropertyCaching {
   void enable();

   void disable();

   void setTimeToLive(Duration timeToLive);

   void clear();

   ConfigurationPropertyCaching.CacheOverride override();

   static ConfigurationPropertyCaching get(Environment environment) {
      return get(environment, null);
   }

   static ConfigurationPropertyCaching get(Environment environment, @Nullable Object underlyingSource) {
      Iterable<ConfigurationPropertySource> sources = ConfigurationPropertySources.get(environment);
      return get(sources, underlyingSource);
   }

   static ConfigurationPropertyCaching get(Iterable<ConfigurationPropertySource> sources) {
      return get(sources, null);
   }

   static ConfigurationPropertyCaching get(Iterable<ConfigurationPropertySource> sources, @Nullable Object underlyingSource) {
      Assert.notNull(sources, "'sources' must not be null");
      if (underlyingSource == null) {
         return new ConfigurationPropertySourcesCaching(sources);
      }

      for (ConfigurationPropertySource source : sources) {
         if (source.getUnderlyingSource() == underlyingSource) {
            ConfigurationPropertyCaching caching = CachingConfigurationPropertySource.find(source);
            if (caching != null) {
               return caching;
            }
         }
      }

      throw new IllegalStateException("Unable to find cache from configuration property sources");
   }

   interface CacheOverride extends AutoCloseable {
      @Override
      void close();
   }
}
