package org.springframework.boot.context.properties.source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

class ConfigurationPropertySourcesCaching implements ConfigurationPropertyCaching {
   private final @Nullable Iterable<ConfigurationPropertySource> sources;

   ConfigurationPropertySourcesCaching(@Nullable Iterable<ConfigurationPropertySource> sources) {
      this.sources = sources;
   }

   @Override
   public void enable() {
      this.forEach(ConfigurationPropertyCaching::enable);
   }

   @Override
   public void disable() {
      this.forEach(ConfigurationPropertyCaching::disable);
   }

   @Override
   public void setTimeToLive(Duration timeToLive) {
      this.forEach(caching -> caching.setTimeToLive(timeToLive));
   }

   @Override
   public void clear() {
      this.forEach(ConfigurationPropertyCaching::clear);
   }

   @Override
   public ConfigurationPropertyCaching.CacheOverride override() {
      ConfigurationPropertySourcesCaching.CacheOverrides override = new ConfigurationPropertySourcesCaching.CacheOverrides();
      this.forEach(override::add);
      return override;
   }

   private void forEach(Consumer<ConfigurationPropertyCaching> action) {
      if (this.sources != null) {
         for (ConfigurationPropertySource source : this.sources) {
            ConfigurationPropertyCaching caching = CachingConfigurationPropertySource.find(source);
            if (caching != null) {
               action.accept(caching);
            }
         }
      }
   }

   private final class CacheOverrides implements ConfigurationPropertyCaching.CacheOverride {
      private List<ConfigurationPropertyCaching.CacheOverride> overrides = new ArrayList<>();

      void add(ConfigurationPropertyCaching caching) {
         this.overrides.add(caching.override());
      }

      @Override
      public void close() {
         this.overrides.forEach(ConfigurationPropertyCaching.CacheOverride::close);
      }
   }
}
