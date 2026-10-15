package org.springframework.boot.context.properties.source;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.Assert;

class SpringConfigurationPropertySource implements ConfigurationPropertySource {
   private static final PropertyMapper[] DEFAULT_MAPPERS = new PropertyMapper[]{DefaultPropertyMapper.INSTANCE};
   private static final PropertyMapper[] SYSTEM_ENVIRONMENT_MAPPERS = new PropertyMapper[]{
      SystemEnvironmentPropertyMapper.INSTANCE, DefaultPropertyMapper.INSTANCE
   };
   private final PropertySource<?> propertySource;
   private final boolean systemEnvironmentSource;
   private final PropertyMapper[] mappers;

   SpringConfigurationPropertySource(PropertySource<?> propertySource, boolean systemEnvironmentSource, PropertyMapper... mappers) {
      Assert.notNull(propertySource, "'propertySource' must not be null");
      Assert.isTrue(mappers.length > 0, "'mappers' must contain at least one item");
      this.propertySource = propertySource;
      this.systemEnvironmentSource = systemEnvironmentSource;
      this.mappers = mappers;
   }

   @Override
   public @Nullable ConfigurationProperty getConfigurationProperty(@Nullable ConfigurationPropertyName name) {
      if (name == null) {
         return null;
      }

      for (PropertyMapper mapper : this.mappers) {
         try {
            for (String candidate : mapper.map(name)) {
               Object value = this.getPropertySourceProperty(candidate);
               if (value != null) {
                  Origin origin = PropertySourceOrigin.get(this.propertySource, candidate);
                  return ConfigurationProperty.of(this, name, value, origin);
               }
            }
         } catch (Exception var10) {
         }
      }

      return null;
   }

   protected final @Nullable Object getPropertySourceProperty(String name) {
      PropertySource<?> propertySource = this.getPropertySource();
      return !this.systemEnvironmentSource
         ? propertySource.getProperty(name)
         : this.getSystemEnvironmentProperty((Map<String, Object>)((SystemEnvironmentPropertySource)propertySource).getSource(), name);
   }

   @Nullable Object getSystemEnvironmentProperty(Map<String, Object> systemEnvironment, String name) {
      Object value = systemEnvironment.get(name);
      return value != null ? value : systemEnvironment.get(name.toLowerCase(Locale.ROOT));
   }

   @Override
   public ConfigurationPropertyState containsDescendantOf(ConfigurationPropertyName name) {
      PropertySource<?> source = this.getPropertySource();
      Object underlyingSource = source.getSource();
      if (underlyingSource instanceof Random) {
         return containsDescendantOfForRandom("random", name);
      } else {
         return underlyingSource instanceof PropertySource<?> underlyingPropertySource && underlyingPropertySource.getSource() instanceof Random
            ? containsDescendantOfForRandom(source.getName(), name)
            : ConfigurationPropertyState.UNKNOWN;
      }
   }

   private static ConfigurationPropertyState containsDescendantOfForRandom(String prefix, ConfigurationPropertyName name) {
      return name.getNumberOfElements() > 1 && name.getElement(0, ConfigurationPropertyName.Form.DASHED).equals(prefix)
         ? ConfigurationPropertyState.PRESENT
         : ConfigurationPropertyState.ABSENT;
   }

   @Override
   public Object getUnderlyingSource() {
      return this.propertySource;
   }

   protected PropertySource<?> getPropertySource() {
      return this.propertySource;
   }

   protected final boolean isSystemEnvironmentSource() {
      return this.systemEnvironmentSource;
   }

   protected final PropertyMapper[] getMappers() {
      return this.mappers;
   }

   @Override
   public String toString() {
      return this.propertySource.toString();
   }

   static SpringConfigurationPropertySource from(PropertySource<?> source) {
      Assert.notNull(source, "'source' must not be null");
      boolean systemEnvironmentSource = isSystemEnvironmentPropertySource(source);
      PropertyMapper[] mappers = !systemEnvironmentSource ? DEFAULT_MAPPERS : SYSTEM_ENVIRONMENT_MAPPERS;
      return !isFullEnumerable(source)
         ? new SpringConfigurationPropertySource(source, systemEnvironmentSource, mappers)
         : new SpringIterableConfigurationPropertySource((EnumerablePropertySource<?>)source, systemEnvironmentSource, mappers);
   }

   private static boolean isSystemEnvironmentPropertySource(PropertySource<?> source) {
      String name = source.getName();
      return source instanceof SystemEnvironmentPropertySource && ("systemEnvironment".equals(name) || name.endsWith("-systemEnvironment"));
   }

   private static boolean isFullEnumerable(PropertySource<?> source) {
      PropertySource<?> rootSource = getRootSource(source);
      if (rootSource.getSource() instanceof Map<?, ?> map) {
         try {
            map.size();
         } catch (UnsupportedOperationException ex) {
            return false;
         }
      }

      return source instanceof EnumerablePropertySource;
   }

   private static PropertySource<?> getRootSource(PropertySource<?> source) {
      while (source.getSource() instanceof PropertySource<?> propertySource) {
         source = propertySource;
      }

      return source;
   }
}
