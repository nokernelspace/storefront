package org.springframework.aot.hint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

public class ResourceHints {
   private final Set<TypeReference> types = new HashSet<>();
   private final List<ResourcePatternHints> resourcePatternHints = new ArrayList<>();
   private final Set<ResourceBundleHint> resourceBundleHints = new LinkedHashSet<>();

   public Stream<ResourcePatternHints> resourcePatternHints() {
      Stream<ResourcePatternHints> patterns = this.resourcePatternHints.stream();
      return this.types.isEmpty() ? patterns : Stream.concat(Stream.of(this.typesPatternResourceHint()), patterns);
   }

   public Stream<ResourceBundleHint> resourceBundleHints() {
      return this.resourceBundleHints.stream();
   }

   public ResourceHints registerPatternIfPresent(@Nullable ClassLoader classLoader, String location, Consumer<ResourcePatternHints.Builder> resourceHint) {
      ClassLoader classLoaderToUse = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
      if (classLoaderToUse != null && classLoaderToUse.getResource(location) != null) {
         this.registerPattern(resourceHint);
      }

      return this;
   }

   public ResourceHints registerPattern(@Nullable Consumer<ResourcePatternHints.Builder> resourceHint) {
      ResourcePatternHints.Builder builder = new ResourcePatternHints.Builder();
      if (resourceHint != null) {
         resourceHint.accept(builder);
      }

      this.resourcePatternHints.add(builder.build());
      return this;
   }

   public ResourceHints registerPattern(String include) {
      return this.registerPattern(builder -> builder.includes(include));
   }

   public void registerResource(Resource resource) {
      if (resource instanceof ClassPathResource classPathResource && classPathResource.exists()) {
         this.registerPattern(classPathResource.getPath());
      } else {
         throw new IllegalArgumentException("Resource must be a ClassPathResource that exists: " + resource);
      }
   }

   public ResourceHints registerType(TypeReference type) {
      this.types.add(type);
      return this;
   }

   public ResourceHints registerType(Class<?> type) {
      return this.registerType(TypeReference.of(type));
   }

   public ResourceHints registerResourceBundle(String baseName, @Nullable Consumer<ResourceBundleHint.Builder> resourceHint) {
      ResourceBundleHint.Builder builder = new ResourceBundleHint.Builder(baseName);
      if (resourceHint != null) {
         resourceHint.accept(builder);
      }

      this.resourceBundleHints.add(builder.build());
      return this;
   }

   public ResourceHints registerResourceBundle(String baseName) {
      return this.registerResourceBundle(baseName, null);
   }

   private ResourcePatternHints typesPatternResourceHint() {
      ResourcePatternHints.Builder builder = new ResourcePatternHints.Builder();
      this.types.forEach(type -> builder.includes(this.toIncludePattern(type)));
      return builder.build();
   }

   private String toIncludePattern(TypeReference type) {
      return type.getName().replace(".", "/") + ".class";
   }
}
