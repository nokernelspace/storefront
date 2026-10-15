package org.springframework.boot.context.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;

public class InvalidConfigDataPropertyException extends ConfigDataException {
   private static final Map<ConfigurationPropertyName, ConfigurationPropertyName> ERRORS;
   private static final Set<ConfigurationPropertyName> PROFILE_SPECIFIC_ERRORS;
   private final ConfigurationProperty property;
   private final @Nullable ConfigurationPropertyName replacement;
   private final @Nullable ConfigDataResource location;

   InvalidConfigDataPropertyException(
      ConfigurationProperty property, boolean profileSpecific, @Nullable ConfigurationPropertyName replacement, @Nullable ConfigDataResource location
   ) {
      super(getMessage(property, profileSpecific, replacement, location), null);
      this.property = property;
      this.replacement = replacement;
      this.location = location;
   }

   public ConfigurationProperty getProperty() {
      return this.property;
   }

   public @Nullable ConfigDataResource getLocation() {
      return this.location;
   }

   public @Nullable ConfigurationPropertyName getReplacement() {
      return this.replacement;
   }

   static void throwIfPropertyFound(ConfigDataEnvironmentContributor contributor) {
      ConfigurationPropertySource propertySource = contributor.getConfigurationPropertySource();
      if (propertySource != null) {
         ERRORS.forEach((name, replacement) -> {
            ConfigurationProperty property = propertySource.getConfigurationProperty(name);
            if (property != null) {
               throw new InvalidConfigDataPropertyException(property, false, replacement, contributor.getResource());
            }
         });
         if (contributor.isFromProfileSpecificImport() && !contributor.hasConfigDataOption(ConfigData.Option.IGNORE_PROFILES)) {
            PROFILE_SPECIFIC_ERRORS.forEach(name -> {
               ConfigurationProperty property = propertySource.getConfigurationProperty(name);
               if (property != null) {
                  throw new InvalidConfigDataPropertyException(property, true, null, contributor.getResource());
               }
            });
         }
      }
   }

   private static String getMessage(
      ConfigurationProperty property, boolean profileSpecific, @Nullable ConfigurationPropertyName replacement, @Nullable ConfigDataResource location
   ) {
      StringBuilder message = new StringBuilder("Property '");
      message.append(property.getName());
      if (location != null) {
         message.append("' imported from location '");
         message.append(location);
      }

      message.append("' is invalid");
      if (profileSpecific) {
         message.append(" in a profile specific resource");
      }

      if (replacement != null) {
         message.append(" and should be replaced with '");
         message.append(replacement);
         message.append("'");
      }

      if (property.getOrigin() != null) {
         message.append(" [origin: ");
         message.append(property.getOrigin());
         message.append("]");
      }

      return message.toString();
   }

   static {
      Map<ConfigurationPropertyName, ConfigurationPropertyName> errors = new LinkedHashMap<>();
      errors.put(ConfigurationPropertyName.of("spring.profiles"), ConfigurationPropertyName.of("spring.config.activate.on-profile"));
      errors.put(ConfigurationPropertyName.of("spring.profiles[0]"), ConfigurationPropertyName.of("spring.config.activate.on-profile"));
      ERRORS = Collections.unmodifiableMap(errors);
      Set<ConfigurationPropertyName> errorsx = new LinkedHashSet<>();
      errorsx.add(Profiles.INCLUDE_PROFILES);
      errorsx.add(Profiles.INCLUDE_PROFILES.append("[0]"));
      errorsx.add(ConfigurationPropertyName.of("spring.profiles.active"));
      errorsx.add(ConfigurationPropertyName.of("spring.profiles.active[0]"));
      errorsx.add(ConfigurationPropertyName.of("spring.profiles.default"));
      errorsx.add(ConfigurationPropertyName.of("spring.profiles.default[0]"));
      PROFILE_SPECIFIC_ERRORS = Collections.unmodifiableSet(errorsx);
   }
}
