package org.springframework.aot.hint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ResourcePatternHints {
   private final List<ResourcePatternHint> includes;

   private ResourcePatternHints(ResourcePatternHints.Builder builder) {
      this.includes = new ArrayList<>(builder.includes);
   }

   public List<ResourcePatternHint> getIncludes() {
      return this.includes;
   }

   public static class Builder {
      private final Set<ResourcePatternHint> includes = new LinkedHashSet<>();

      Builder() {
      }

      public ResourcePatternHints.Builder includes(@Nullable TypeReference reachableType, String... includes) {
         Arrays.stream(includes)
            .map(this::expandToIncludeDirectories)
            .flatMap(Collection::stream)
            .map(include -> new ResourcePatternHint(include, reachableType))
            .forEach(this.includes::add);
         return this;
      }

      private List<String> expandToIncludeDirectories(String includePattern) {
         if (!includePattern.contains("/")) {
            return List.of("/", includePattern);
         }

         List<String> includePatterns = new ArrayList<>();
         includePatterns.add("/");
         includePatterns.add(includePattern);
         StringBuilder path = new StringBuilder();

         for (String pathElement : includePattern.split("/")) {
            if (!pathElement.isEmpty()) {
               if (pathElement.contains("*")) {
                  break;
               }

               if (!path.isEmpty()) {
                  path.append("/");
               }

               path.append(pathElement);
               includePatterns.add(path.toString());
            }
         }

         return includePatterns;
      }

      public ResourcePatternHints.Builder includes(String... includes) {
         return this.includes(null, includes);
      }

      ResourcePatternHints build() {
         return new ResourcePatternHints(this);
      }
   }
}
