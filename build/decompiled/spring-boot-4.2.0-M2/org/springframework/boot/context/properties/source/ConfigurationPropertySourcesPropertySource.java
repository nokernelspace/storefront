package org.springframework.boot.context.properties.source;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.PropertySource;

class ConfigurationPropertySourcesPropertySource extends PropertySource<Iterable<ConfigurationPropertySource>> implements OriginLookup<String> {
   ConfigurationPropertySourcesPropertySource(String name, Iterable<ConfigurationPropertySource> source) {
      super(name, source);
   }

   public boolean containsProperty(String name) {
      return this.findConfigurationProperty(name) != null;
   }

   public @Nullable Object getProperty(String name) {
      ConfigurationProperty configurationProperty = this.findConfigurationProperty(name);
      return configurationProperty != null ? configurationProperty.getValue() : null;
   }

   public @Nullable Origin getOrigin(String name) {
      return Origin.from(this.findConfigurationProperty(name));
   }

   private @Nullable ConfigurationProperty findConfigurationProperty(String name) {
      try {
         return this.findConfigurationProperty(ConfigurationPropertyName.of(name, true));
      } catch (Exception ex) {
         return null;
      }
   }

   @Nullable ConfigurationProperty findConfigurationProperty(@Nullable ConfigurationPropertyName name) {
      if (name == null) {
         return null;
      }

      for (ConfigurationPropertySource configurationPropertySource : (Iterable)this.getSource()) {
         ConfigurationProperty configurationProperty = configurationPropertySource.getConfigurationProperty(name);
         if (configurationProperty != null) {
            return configurationProperty;
         }
      }

      return null;
   }
}
