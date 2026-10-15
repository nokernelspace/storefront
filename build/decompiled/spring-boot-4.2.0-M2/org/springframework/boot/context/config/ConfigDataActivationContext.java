package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.core.style.ToStringCreator;

class ConfigDataActivationContext {
   private final @Nullable CloudPlatform cloudPlatform;
   private final @Nullable Profiles profiles;

   ConfigDataActivationContext(Environment environment, Binder binder) {
      this.cloudPlatform = this.deduceCloudPlatform(environment, binder);
      this.profiles = null;
   }

   ConfigDataActivationContext(@Nullable CloudPlatform cloudPlatform, @Nullable Profiles profiles) {
      this.cloudPlatform = cloudPlatform;
      this.profiles = profiles;
   }

   private @Nullable CloudPlatform deduceCloudPlatform(Environment environment, Binder binder) {
      for (CloudPlatform candidate : CloudPlatform.values()) {
         if (candidate.isEnforced(binder)) {
            return candidate;
         }
      }

      return CloudPlatform.getActive(environment);
   }

   ConfigDataActivationContext withProfiles(Profiles profiles) {
      return new ConfigDataActivationContext(this.cloudPlatform, profiles);
   }

   @Nullable CloudPlatform getCloudPlatform() {
      return this.cloudPlatform;
   }

   @Nullable Profiles getProfiles() {
      return this.profiles;
   }

   @Override
   public String toString() {
      ToStringCreator creator = new ToStringCreator(this);
      creator.append("cloudPlatform", this.cloudPlatform);
      creator.append("profiles", this.profiles);
      return creator.toString();
   }
}
