package org.springframework.aot.hint.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public final class FilePatternResourceHintsRegistrar {
   private final List<String> classpathLocations;
   private final List<String> filePrefixes;
   private final List<String> fileExtensions;

   private FilePatternResourceHintsRegistrar(List<String> filePrefixes, List<String> classpathLocations, List<String> fileExtensions) {
      this.classpathLocations = validateClassPathLocations(classpathLocations);
      this.filePrefixes = validateFilePrefixes(filePrefixes);
      this.fileExtensions = validateFileExtensions(fileExtensions);
   }

   private void registerHints(ResourceHints hints, @Nullable ClassLoader classLoader) {
      ClassLoader classLoaderToUse = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
      if (classLoaderToUse != null) {
         List<String> includes = new ArrayList<>();

         for (String location : this.classpathLocations) {
            if (classLoaderToUse.getResource(location) != null) {
               for (String filePrefix : this.filePrefixes) {
                  for (String fileExtension : this.fileExtensions) {
                     includes.add(location + filePrefix + "*" + fileExtension);
                  }
               }
            }
         }

         if (!includes.isEmpty()) {
            hints.registerPattern(hint -> hint.includes(includes.toArray(String[]::new)));
         }
      }
   }

   public static FilePatternResourceHintsRegistrar.Builder forClassPathLocations(String... classpathLocations) {
      return forClassPathLocations(Arrays.asList(classpathLocations));
   }

   public static FilePatternResourceHintsRegistrar.Builder forClassPathLocations(List<String> classpathLocations) {
      return new FilePatternResourceHintsRegistrar.Builder().withClassPathLocations(classpathLocations);
   }

   private static List<String> validateClassPathLocations(List<String> classpathLocations) {
      Assert.notEmpty(classpathLocations, "At least one classpath location must be specified");
      List<String> parsedLocations = new ArrayList<>();

      for (String location : classpathLocations) {
         if (location.startsWith("classpath:")) {
            location = location.substring("classpath:".length());
         }

         if (location.startsWith("/")) {
            location = location.substring(1);
         }

         if (!location.isEmpty() && !location.endsWith("/")) {
            location = location + "/";
         }

         parsedLocations.add(location);
      }

      return parsedLocations;
   }

   private static List<String> validateFilePrefixes(List<String> filePrefixes) {
      for (String filePrefix : filePrefixes) {
         if (filePrefix.contains("*")) {
            throw new IllegalArgumentException("File prefix '" + filePrefix + "' cannot contain '*'");
         }
      }

      return filePrefixes;
   }

   private static List<String> validateFileExtensions(List<String> fileExtensions) {
      for (String fileExtension : fileExtensions) {
         if (!fileExtension.startsWith(".")) {
            throw new IllegalArgumentException("Extension '" + fileExtension + "' must start with '.'");
         }
      }

      return fileExtensions;
   }

   public static final class Builder {
      private final List<String> classpathLocations = new ArrayList<>();
      private final List<String> filePrefixes = new ArrayList<>();
      private final List<String> fileExtensions = new ArrayList<>();

      private Builder() {
      }

      @Deprecated(since = "7.0", forRemoval = true)
      public FilePatternResourceHintsRegistrar.Builder withClasspathLocations(String... classpathLocations) {
         return this.withClassPathLocations(Arrays.asList(classpathLocations));
      }

      @Deprecated(since = "7.0", forRemoval = true)
      public FilePatternResourceHintsRegistrar.Builder withClasspathLocations(List<String> classpathLocations) {
         return this.withClassPathLocations(classpathLocations);
      }

      public FilePatternResourceHintsRegistrar.Builder withClassPathLocations(String... classpathLocations) {
         return this.withClassPathLocations(Arrays.asList(classpathLocations));
      }

      public FilePatternResourceHintsRegistrar.Builder withClassPathLocations(List<String> classpathLocations) {
         this.classpathLocations.addAll(FilePatternResourceHintsRegistrar.validateClassPathLocations(classpathLocations));
         return this;
      }

      public FilePatternResourceHintsRegistrar.Builder withFilePrefixes(String... filePrefixes) {
         return this.withFilePrefixes(Arrays.asList(filePrefixes));
      }

      public FilePatternResourceHintsRegistrar.Builder withFilePrefixes(List<String> filePrefixes) {
         this.filePrefixes.addAll(FilePatternResourceHintsRegistrar.validateFilePrefixes(filePrefixes));
         return this;
      }

      public FilePatternResourceHintsRegistrar.Builder withFileExtensions(String... fileExtensions) {
         return this.withFileExtensions(Arrays.asList(fileExtensions));
      }

      public FilePatternResourceHintsRegistrar.Builder withFileExtensions(List<String> fileExtensions) {
         this.fileExtensions.addAll(FilePatternResourceHintsRegistrar.validateFileExtensions(fileExtensions));
         return this;
      }

      private FilePatternResourceHintsRegistrar build() {
         return new FilePatternResourceHintsRegistrar(this.filePrefixes, this.classpathLocations, this.fileExtensions);
      }

      public void registerHints(ResourceHints hints, @Nullable ClassLoader classLoader) {
         this.build().registerHints(hints, classLoader);
      }
   }
}
