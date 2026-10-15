package org.springframework.boot.context.properties.source;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.env.AbstractPropertyResolver;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

class ConfigurationPropertySourcesPropertyResolver extends AbstractPropertyResolver {
   private final MutablePropertySources propertySources;
   private final ConfigurationPropertySourcesPropertyResolver.DefaultResolver defaultResolver;

   ConfigurationPropertySourcesPropertyResolver(MutablePropertySources propertySources) {
      this.propertySources = propertySources;
      this.defaultResolver = new ConfigurationPropertySourcesPropertyResolver.DefaultResolver(propertySources);
   }

   public boolean containsProperty(String key) {
      ConfigurationPropertySourcesPropertySource attached = this.getAttached();
      if (attached != null) {
         ConfigurationPropertyName name = ConfigurationPropertyName.of(key, true);
         if (name != null) {
            try {
               return attached.findConfigurationProperty(name) != null;
            } catch (Exception var5) {
            }
         }
      }

      return this.defaultResolver.containsProperty(key);
   }

   public @Nullable String getProperty(String key) {
      return this.getProperty(key, String.class, true);
   }

   public <T> @Nullable T getProperty(String key, Class<T> targetValueType) {
      return this.getProperty(key, targetValueType, true);
   }

   protected @Nullable String getPropertyAsRawString(String key) {
      return this.getProperty(key, String.class, false);
   }

   private <T> @Nullable T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
      Object value = this.findPropertyValue(key);
      if (value == null) {
         return null;
      }

      if (resolveNestedPlaceholders && value instanceof String string) {
         value = this.resolveNestedPlaceholders(string);
      }

      try {
         return (T)this.convertValueIfNecessary(value, targetValueType);
      } catch (ConversionFailedException ex) {
         Exception wrappedCause = new InvalidConfigurationPropertyValueException(key, value, "Failed to convert to type " + ex.getTargetType(), ex.getCause());
         throw new ConversionFailedException(ex.getSourceType(), ex.getTargetType(), ex.getValue(), wrappedCause);
      }
   }

   private @Nullable Object findPropertyValue(String key) {
      ConfigurationPropertySourcesPropertySource attached = this.getAttached();
      if (attached != null) {
         ConfigurationPropertyName name = ConfigurationPropertyName.of(key, true);
         if (name != null) {
            try {
               ConfigurationProperty configurationProperty = attached.findConfigurationProperty(name);
               return configurationProperty != null ? configurationProperty.getValue() : null;
            } catch (Exception var5) {
            }
         }
      }

      return this.defaultResolver.getProperty(key, Object.class, false);
   }

   private @Nullable ConfigurationPropertySourcesPropertySource getAttached() {
      ConfigurationPropertySourcesPropertySource attached = (ConfigurationPropertySourcesPropertySource)ConfigurationPropertySources.getAttached(
         this.propertySources
      );
      return (attached != null ? (Iterable)attached.getSource() : null) instanceof SpringConfigurationPropertySources springSource
            && springSource.isUsingSources(this.propertySources)
         ? attached
         : null;
   }

   static class DefaultResolver extends PropertySourcesPropertyResolver {
      DefaultResolver(PropertySources propertySources) {
         super(propertySources);
      }

      public <T> @Nullable T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
         return (T)super.getProperty(key, targetValueType, resolveNestedPlaceholders);
      }
   }
}
