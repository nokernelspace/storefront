package org.springframework.aot.hint.predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.aot.hint.ResourcePatternHint;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

public class ResourceHintsPredicates {
   private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

   ResourceHintsPredicates() {
   }

   public Predicate<RuntimeHints> forBundle(String bundleName) {
      Assert.hasText(bundleName, "resource bundle name should not be empty");
      return runtimeHints -> runtimeHints.resources().resourceBundleHints().anyMatch(bundleHint -> bundleName.equals(bundleHint.getBaseName()));
   }

   public Predicate<RuntimeHints> forResource(TypeReference type, String resourceName) {
      String absoluteName = this.resolveAbsoluteResourceName(type, resourceName);
      return this.forResource(absoluteName);
   }

   private String resolveAbsoluteResourceName(TypeReference type, String resourceName) {
      if (resourceName.startsWith("/")) {
         return resourceName.substring(1);
      } else {
         return type.getPackageName().isEmpty() ? resourceName : type.getPackageName().replace('.', '/') + "/" + resourceName;
      }
   }

   public Predicate<RuntimeHints> forResource(String resourceName) {
      String resourceNameToUse = resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
      return hints -> {
         ResourceHintsPredicates.AggregatedResourcePatternHints aggregatedResourcePatternHints = ResourceHintsPredicates.AggregatedResourcePatternHints.of(
            hints.resources()
         );
         return aggregatedResourcePatternHints.includes().stream().anyMatch(included -> PATH_MATCHER.match(included.getPattern(), resourceNameToUse));
      };
   }

   private record AggregatedResourcePatternHints(List<ResourcePatternHint> includes) {
      static ResourceHintsPredicates.AggregatedResourcePatternHints of(ResourceHints resourceHints) {
         List<ResourcePatternHint> includes = new ArrayList<>();
         resourceHints.resourcePatternHints().forEach(resourcePatternHint -> includes.addAll(resourcePatternHint.getIncludes()));
         return new ResourceHintsPredicates.AggregatedResourcePatternHints(includes);
      }
   }
}
