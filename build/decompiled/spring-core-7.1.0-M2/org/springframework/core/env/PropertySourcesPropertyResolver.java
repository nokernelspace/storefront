package org.springframework.core.env;

import org.jspecify.annotations.Nullable;

public class PropertySourcesPropertyResolver extends AbstractPropertyResolver {
   private final @Nullable PropertySources propertySources;

   public PropertySourcesPropertyResolver(@Nullable PropertySources propertySources) {
      this.propertySources = propertySources;
   }

   @Override
   public boolean containsProperty(String key) {
      if (this.propertySources != null) {
         for (PropertySource<?> propertySource : this.propertySources) {
            if (propertySource.containsProperty(key)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public @Nullable String getProperty(String key) {
      return this.getProperty(key, String.class, true);
   }

   @Override
   public <T> @Nullable T getProperty(String key, Class<T> targetValueType) {
      return this.getProperty(key, targetValueType, true);
   }

   @Override
   protected @Nullable String getPropertyAsRawString(String key) {
      return this.getProperty(key, String.class, false);
   }

   protected <T> @Nullable T getProperty(String key, Class<T> targetValueType, boolean resolveNestedPlaceholders) {
      if (this.propertySources != null) {
         for (PropertySource<?> propertySource : this.propertySources) {
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Searching for key '" + key + "' in PropertySource '" + propertySource.getName() + "'");
            }

            Object value = propertySource.getProperty(key);
            if (value != null) {
               if (resolveNestedPlaceholders) {
                  if (value instanceof String string) {
                     value = this.resolveNestedPlaceholders(string);
                  } else if (value instanceof CharSequence cs && (String.class.equals(targetValueType) || CharSequence.class.equals(targetValueType))) {
                     value = this.resolveNestedPlaceholders(cs.toString());
                  }
               }

               this.logKeyFound(key, propertySource, value);
               return this.convertValueIfNecessary(value, targetValueType);
            }
         }
      }

      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Could not find key '" + key + "' in any property source");
      }

      return null;
   }

   protected void logKeyFound(String key, PropertySource<?> propertySource, Object value) {
      if (this.logger.isDebugEnabled()) {
         this.logger
            .debug("Found key '" + key + "' in PropertySource '" + propertySource.getName() + "' with value of type " + value.getClass().getSimpleName());
      }
   }
}
