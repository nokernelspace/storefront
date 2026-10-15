package org.springframework.boot.context.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class ConfigDataResourceNotFoundException extends ConfigDataNotFoundException {
   private final ConfigDataResource resource;
   private final @Nullable ConfigDataLocation location;

   public ConfigDataResourceNotFoundException(ConfigDataResource resource) {
      this(resource, null);
   }

   public ConfigDataResourceNotFoundException(ConfigDataResource resource, @Nullable Throwable cause) {
      this(resource, null, cause);
   }

   private ConfigDataResourceNotFoundException(ConfigDataResource resource, @Nullable ConfigDataLocation location, @Nullable Throwable cause) {
      super(getMessage(resource, location), cause);
      Assert.notNull(resource, "'resource' must not be null");
      this.resource = resource;
      this.location = location;
   }

   public ConfigDataResource getResource() {
      return this.resource;
   }

   public @Nullable ConfigDataLocation getLocation() {
      return this.location;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return Origin.from(this.location);
   }

   @Override
   public String getReferenceDescription() {
      return getReferenceDescription(this.resource, this.location);
   }

   ConfigDataResourceNotFoundException withLocation(ConfigDataLocation location) {
      return new ConfigDataResourceNotFoundException(this.resource, location, this.getCause());
   }

   private static String getMessage(ConfigDataResource resource, @Nullable ConfigDataLocation location) {
      return String.format("Config data %s cannot be found", getReferenceDescription(resource, location));
   }

   private static String getReferenceDescription(ConfigDataResource resource, @Nullable ConfigDataLocation location) {
      String description = String.format("resource '%s'", resource);
      if (location != null) {
         description = description + String.format(" via location '%s'", location);
      }

      return description;
   }

   public static void throwIfDoesNotExist(ConfigDataResource resource, Path pathToCheck) {
      throwIfNot(resource, Files.exists(pathToCheck));
   }

   public static void throwIfDoesNotExist(ConfigDataResource resource, File fileToCheck) {
      throwIfNot(resource, fileToCheck.exists());
   }

   public static void throwIfDoesNotExist(ConfigDataResource resource, Resource resourceToCheck) {
      throwIfNot(resource, resourceToCheck.exists());
   }

   private static void throwIfNot(ConfigDataResource resource, boolean check) {
      if (!check) {
         throw new ConfigDataResourceNotFoundException(resource);
      }
   }
}
