package org.springframework.boot.context.config;

class ConfigDataResolutionResult {
   private final ConfigDataLocation location;
   private final ConfigDataResource resource;
   private final boolean profileSpecific;

   ConfigDataResolutionResult(ConfigDataLocation location, ConfigDataResource resource, boolean profileSpecific) {
      this.location = location;
      this.resource = resource;
      this.profileSpecific = profileSpecific;
   }

   ConfigDataLocation getLocation() {
      return this.location;
   }

   ConfigDataResource getResource() {
      return this.resource;
   }

   boolean isProfileSpecific() {
      return this.profileSpecific;
   }
}
