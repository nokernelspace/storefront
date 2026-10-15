package org.springframework.boot.context.config;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

class ConfigDataProperties {
   private static final ConfigurationPropertyName NAME = ConfigurationPropertyName.of("spring.config");
   private static final Bindable<ConfigDataProperties> BINDABLE_PROPERTIES = Bindable.of(ConfigDataProperties.class);
   private final List<ConfigDataLocation> imports;
   private final ConfigDataProperties.@Nullable Activate activate;

   ConfigDataProperties(@Name("import") @Nullable List<ConfigDataLocation> imports, ConfigDataProperties.@Nullable Activate activate) {
      this.imports = imports != null ? imports.stream().filter(ConfigDataLocation::isNotEmpty).toList() : Collections.emptyList();
      this.activate = activate;
   }

   List<ConfigDataLocation> getImports() {
      return this.imports;
   }

   boolean isActive(@Nullable ConfigDataActivationContext activationContext) {
      return this.activate == null || this.activate.isActive(activationContext);
   }

   ConfigDataProperties withoutImports() {
      return new ConfigDataProperties(null, this.activate);
   }

   static @Nullable ConfigDataProperties get(Binder binder) {
      return binder.bind(NAME, BINDABLE_PROPERTIES, new ConfigDataLocationBindHandler()).orElse(null);
   }

   static class Activate {
      private final @Nullable CloudPlatform onCloudPlatform;
      private final String @Nullable [] onProfile;

      Activate(@Nullable CloudPlatform onCloudPlatform, String @Nullable [] onProfile) {
         this.onProfile = onProfile;
         this.onCloudPlatform = onCloudPlatform;
      }

      boolean isActive(@Nullable ConfigDataActivationContext activationContext) {
         if (activationContext == null) {
            return false;
         }

         CloudPlatform cloudPlatform = activationContext.getCloudPlatform();
         boolean activate = this.isActive(cloudPlatform != null ? cloudPlatform : CloudPlatform.NONE);
         return activate && this.isActive(activationContext.getProfiles());
      }

      private boolean isActive(CloudPlatform cloudPlatform) {
         return this.onCloudPlatform == null || this.onCloudPlatform == cloudPlatform;
      }

      private boolean isActive(@Nullable Profiles profiles) {
         return ObjectUtils.isEmpty(this.onProfile) || profiles != null && this.matchesActiveProfiles(profiles::isAccepted);
      }

      private boolean matchesActiveProfiles(Predicate<String> activeProfiles) {
         Assert.state(this.onProfile != null, "'this.onProfile' must not be null");
         return org.springframework.core.env.Profiles.of(this.onProfile).matches(activeProfiles);
      }
   }
}
