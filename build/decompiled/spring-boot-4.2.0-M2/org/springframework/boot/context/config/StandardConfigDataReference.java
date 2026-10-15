package org.springframework.boot.context.config;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.util.StringUtils;

class StandardConfigDataReference {
   private final ConfigDataLocation configDataLocation;
   private final String resourceLocation;
   private final @Nullable String directory;
   private final @Nullable String profile;
   private final PropertySourceLoader propertySourceLoader;
   private final @Nullable Charset encoding;

   StandardConfigDataReference(
      ConfigDataLocation configDataLocation,
      @Nullable String directory,
      String root,
      @Nullable String profile,
      @Nullable String extension,
      PropertySourceLoader propertySourceLoader,
      @Nullable Charset encoding
   ) {
      this.configDataLocation = configDataLocation;
      String profileSuffix = StringUtils.hasText(profile) ? "-" + profile : "";
      this.resourceLocation = root + profileSuffix + (extension != null ? "." + extension : "");
      this.directory = directory;
      this.profile = profile;
      this.propertySourceLoader = propertySourceLoader;
      this.encoding = encoding;
   }

   ConfigDataLocation getConfigDataLocation() {
      return this.configDataLocation;
   }

   String getResourceLocation() {
      return this.resourceLocation;
   }

   boolean isMandatoryDirectory() {
      return !this.configDataLocation.isOptional() && this.directory != null;
   }

   @Nullable String getDirectory() {
      return this.directory;
   }

   @Nullable String getProfile() {
      return this.profile;
   }

   @Nullable Charset getEncoding() {
      return this.encoding;
   }

   boolean isSkippable() {
      return this.configDataLocation.isOptional() || this.directory != null || this.profile != null;
   }

   PropertySourceLoader getPropertySourceLoader() {
      return this.propertySourceLoader;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         StandardConfigDataReference other = (StandardConfigDataReference)obj;
         return this.resourceLocation.equals(other.resourceLocation);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.resourceLocation.hashCode();
   }

   @Override
   public String toString() {
      return this.resourceLocation;
   }
}
