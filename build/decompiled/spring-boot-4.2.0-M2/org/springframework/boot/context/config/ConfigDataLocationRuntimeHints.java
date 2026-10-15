package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.support.FilePatternResourceHintsRegistrar;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ConfigDataLocationRuntimeHints implements RuntimeHintsRegistrar {
   private static final Log logger = LogFactory.getLog(ConfigDataLocationRuntimeHints.class);

   public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      List<String> fileNames = this.getFileNames(classLoader);
      List<String> locations = this.getLocations(classLoader);
      List<String> extensions = this.getExtensions(classLoader);
      if (logger.isDebugEnabled()) {
         logger.debug("Registering application configuration hints for " + fileNames + "(" + extensions + ") at " + locations);
      }

      FilePatternResourceHintsRegistrar.forClassPathLocations(locations)
         .withFilePrefixes(fileNames)
         .withFileExtensions(extensions)
         .registerHints(hints.resources(), classLoader);
   }

   protected List<String> getFileNames(@Nullable ClassLoader classLoader) {
      return Arrays.asList(StandardConfigDataLocationResolver.DEFAULT_CONFIG_NAMES);
   }

   protected List<String> getLocations(@Nullable ClassLoader classLoader) {
      List<String> classpathLocations = new ArrayList<>();

      for (ConfigDataLocation candidate : ConfigDataEnvironment.DEFAULT_SEARCH_LOCATIONS) {
         for (ConfigDataLocation configDataLocation : candidate.split()) {
            String location = configDataLocation.getValue();
            if (location.startsWith("classpath:")) {
               classpathLocations.add(location);
            }
         }
      }

      return classpathLocations;
   }

   protected List<String> getExtensions(@Nullable ClassLoader classLoader) {
      List<String> extensions = new ArrayList<>();

      for (PropertySourceLoader propertySourceLoader : this.getSpringFactoriesLoader(classLoader).load(PropertySourceLoader.class)) {
         for (String fileExtension : propertySourceLoader.getFileExtensions()) {
            String candidate = "." + fileExtension;
            if (!extensions.contains(candidate)) {
               extensions.add(candidate);
            }
         }
      }

      return extensions;
   }

   protected SpringFactoriesLoader getSpringFactoriesLoader(@Nullable ClassLoader classLoader) {
      return SpringFactoriesLoader.forDefaultResourceLocation(classLoader);
   }
}
