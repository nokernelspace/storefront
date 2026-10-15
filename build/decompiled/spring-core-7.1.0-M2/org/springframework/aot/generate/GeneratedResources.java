package org.springframework.aot.generate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.ClassName;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class GeneratedResources {
   private final NameGenerator nameGenerator;
   private final List<GeneratedResource> resources;
   private final Map<String, GeneratedResource> resourcesByPath;
   private final Map<GeneratedResources.Owner, GeneratedResource> resourcesByOwner;

   GeneratedResources(NameGenerator nameGenerator) {
      this(nameGenerator, new ArrayList<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
   }

   private GeneratedResources(
      NameGenerator nameGenerator,
      List<GeneratedResource> resources,
      Map<String, GeneratedResource> resourcesByPath,
      Map<GeneratedResources.Owner, GeneratedResource> resourcesByOwner
   ) {
      this.nameGenerator = nameGenerator;
      this.resources = resources;
      this.resourcesByPath = resourcesByPath;
      this.resourcesByOwner = resourcesByOwner;
   }

   public GeneratedResource getOrAdd(String path) {
      Assert.hasLength(path, "'path' must not be empty");
      if (!path.equals(cleanedPath(path))) {
         throw new IllegalArgumentException("Invalid classpath location '%s'".formatted(path));
      } else {
         return this.resourcesByPath.computeIfAbsent(path, key -> {
            GeneratedResource resource = new GeneratedResource(path);
            this.resources.add(resource);
            return resource;
         });
      }
   }

   private static String cleanedPath(String path) {
      String cleanedPath = StringUtils.cleanPath(path);
      if (cleanedPath.startsWith("/")) {
         cleanedPath = cleanedPath.substring(1);
      }

      return cleanedPath;
   }

   public GeneratedResource getOrAddForFeature(String extension, String featureName) {
      Assert.hasLength(extension, "'extension' must not be empty");
      Assert.hasLength(featureName, "'featureName' must not be empty");
      GeneratedResources.Owner owner = new GeneratedResources.Owner(extension, this.nameGenerator.getFeatureNamePrefix(), featureName, null);
      return this.resourcesByOwner.computeIfAbsent(owner, key -> this.createAndAddGeneratedResource(extension, featureName, null));
   }

   public GeneratedResource getOrAddForFeatureComponent(String extension, String featureName, ClassName targetComponent) {
      Assert.hasLength(extension, "'extension' must not be empty");
      Assert.hasLength(featureName, "'featureName' must not be empty");
      Assert.notNull(targetComponent, "'targetComponent' must not be null");
      GeneratedResources.Owner owner = new GeneratedResources.Owner(extension, this.nameGenerator.getFeatureNamePrefix(), featureName, targetComponent);
      return this.resourcesByOwner.computeIfAbsent(owner, key -> this.createAndAddGeneratedResource(extension, featureName, targetComponent));
   }

   public GeneratedResource addForFeature(String extension, String featureName) {
      Assert.hasLength(extension, "'extension' must not be empty");
      Assert.hasLength(featureName, "'featureName' must not be empty");
      return this.createAndAddGeneratedResource(extension, featureName, null);
   }

   public GeneratedResource addForFeatureComponent(String extension, String featureName, ClassName targetComponent) {
      Assert.hasLength(extension, "'extension' must not be empty");
      Assert.hasLength(featureName, "'featureName' must not be empty");
      Assert.notNull(targetComponent, "'targetComponent' must not be null");
      return this.createAndAddGeneratedResource(extension, featureName, targetComponent);
   }

   private GeneratedResource createAndAddGeneratedResource(String extension, String featureName, @Nullable ClassName targetComponent) {
      String path = this.nameGenerator.generateResourcePath(extension, featureName, targetComponent);
      GeneratedResource generatedResource = new GeneratedResource(path);
      this.resources.add(generatedResource);
      return generatedResource;
   }

   void writeTo(GeneratedFiles generatedFiles) {
      Assert.notNull(generatedFiles, "'generatedFiles' must not be null");
      List<GeneratedResource> generatedResources = new ArrayList<>(this.resources);
      generatedResources.sort(Comparator.comparing(GeneratedResource::getPath));

      for (GeneratedResource generatedResource : generatedResources) {
         generatedResource.writeTo(generatedFiles);
      }
   }

   GeneratedResources withFeatureNamePrefix(String featureNamePrefix) {
      return new GeneratedResources(this.nameGenerator.withFeatureNamePrefix(featureNamePrefix), this.resources, this.resourcesByPath, this.resourcesByOwner);
   }

   private record Owner(String extension, String featureNamePrefix, String featureName, @Nullable ClassName target) {
   }
}
