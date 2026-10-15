package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

public class InactiveConfigDataAccessException extends ConfigDataException {
   private final PropertySource<?> propertySource;
   private final @Nullable ConfigDataResource location;
   private final String propertyName;
   private final @Nullable Origin origin;

   InactiveConfigDataAccessException(PropertySource<?> propertySource, @Nullable ConfigDataResource location, String propertyName, @Nullable Origin origin) {
      super(getMessage(propertySource, location, propertyName, origin), null);
      this.propertySource = propertySource;
      this.location = location;
      this.propertyName = propertyName;
      this.origin = origin;
   }

   private static String getMessage(PropertySource<?> propertySource, @Nullable ConfigDataResource location, String propertyName, @Nullable Origin origin) {
      StringBuilder message = new StringBuilder("Inactive property source '");
      message.append(propertySource.getName());
      if (location != null) {
         message.append("' imported from location '");
         message.append(location);
      }

      message.append("' cannot contain property '");
      message.append(propertyName);
      message.append("'");
      if (origin != null) {
         message.append(" [origin: ");
         message.append(origin);
         message.append("]");
      }

      return message.toString();
   }

   public PropertySource<?> getPropertySource() {
      return this.propertySource;
   }

   public @Nullable ConfigDataResource getLocation() {
      return this.location;
   }

   public String getPropertyName() {
      return this.propertyName;
   }

   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   static void throwIfPropertyFound(ConfigDataEnvironmentContributor contributor, ConfigurationPropertyName name) {
      ConfigurationPropertySource source = contributor.getConfigurationPropertySource();
      ConfigurationProperty property = source != null ? source.getConfigurationProperty(name) : null;
      if (property != null) {
         PropertySource<?> propertySource = contributor.getPropertySource();
         ConfigDataResource location = contributor.getResource();
         Assert.state(propertySource != null, "'propertySource' must not be null");
         throw new InactiveConfigDataAccessException(propertySource, location, name.toString(), property.getOrigin());
      }
   }
}
