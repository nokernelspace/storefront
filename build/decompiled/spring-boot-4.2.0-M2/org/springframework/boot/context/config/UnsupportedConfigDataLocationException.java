package org.springframework.boot.context.config;

public class UnsupportedConfigDataLocationException extends ConfigDataException {
   private final ConfigDataLocation location;

   UnsupportedConfigDataLocationException(ConfigDataLocation location) {
      super("Unsupported config data location '" + location + "'", null);
      this.location = location;
   }

   public ConfigDataLocation getLocation() {
      return this.location;
   }
}
