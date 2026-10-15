package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.util.Assert;

public class ConfigDataLocationNotFoundException extends ConfigDataNotFoundException {
   private final ConfigDataLocation location;

   public ConfigDataLocationNotFoundException(ConfigDataLocation location) {
      this(location, null);
   }

   public ConfigDataLocationNotFoundException(ConfigDataLocation location, @Nullable Throwable cause) {
      this(location, getMessage(location), cause);
   }

   public ConfigDataLocationNotFoundException(ConfigDataLocation location, String message, @Nullable Throwable cause) {
      super(message, cause);
      Assert.notNull(location, "'location' must not be null");
      this.location = location;
   }

   public ConfigDataLocation getLocation() {
      return this.location;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return Origin.from(this.location);
   }

   @Override
   public String getReferenceDescription() {
      return getReferenceDescription(this.location);
   }

   private static String getMessage(ConfigDataLocation location) {
      return String.format("Config data %s cannot be found", getReferenceDescription(location));
   }

   private static String getReferenceDescription(ConfigDataLocation location) {
      return String.format("location '%s'", location);
   }
}
