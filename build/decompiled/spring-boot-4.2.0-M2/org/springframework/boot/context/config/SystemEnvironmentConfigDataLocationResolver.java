package org.springframework.boot.context.config;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;

class SystemEnvironmentConfigDataLocationResolver implements ConfigDataLocationResolver<SystemEnvironmentConfigDataResource> {
   private static final String PREFIX = "env:";
   private static final String DEFAULT_EXTENSION = ".properties";
   private final List<PropertySourceLoader> loaders;
   private final Function<String, @Nullable String> environment;

   SystemEnvironmentConfigDataLocationResolver() {
      this.loaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class, this.getClass().getClassLoader());
      this.environment = System::getenv;
   }

   SystemEnvironmentConfigDataLocationResolver(List<PropertySourceLoader> loaders, Function<String, @Nullable String> environment) {
      this.loaders = loaders;
      this.environment = environment;
   }

   @Override
   public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
      return location.hasPrefix("env:");
   }

   @Override
   public List<SystemEnvironmentConfigDataResource> resolve(ConfigDataLocationResolverContext context, ConfigDataLocation location) throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
      String value = location.getNonPrefixedValue("env:");
      FileHint fileHint = FileHint.from(value);
      String variableName = FileHint.removeFrom(value);
      PropertySourceLoader loader = this.getLoader(fileHint.getExtensionOrElse(".properties"));
      if (this.hasEnvVariable(variableName)) {
         return List.of(new SystemEnvironmentConfigDataResource(variableName, loader, this.environment, fileHint.getEncodingAsCharset()));
      } else if (location.isOptional()) {
         return Collections.emptyList();
      } else {
         throw new ConfigDataLocationNotFoundException(location, "Environment variable '%s' is not set".formatted(variableName), null);
      }
   }

   private PropertySourceLoader getLoader(String extension) {
      extension = !extension.startsWith(".") ? extension : extension.substring(1);

      for (PropertySourceLoader loader : this.loaders) {
         for (String supportedExtension : loader.getFileExtensions()) {
            if (supportedExtension.equalsIgnoreCase(extension)) {
               return loader;
            }
         }
      }

      throw new IllegalStateException("File extension '%s' is not known to any PropertySourceLoader".formatted(extension));
   }

   private boolean hasEnvVariable(String variableName) {
      return this.environment.apply(variableName) != null;
   }
}
