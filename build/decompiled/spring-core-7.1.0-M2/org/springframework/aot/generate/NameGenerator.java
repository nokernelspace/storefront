package org.springframework.aot.generate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.ClassName;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public final class NameGenerator {
   private static final String CLASS_NAME_SEPARATOR = "__";
   private static final String RESOURCE_NAME_SEPARATOR = "-";
   private static final String AOT_FEATURE = "Aot";
   private final ClassName defaultTarget;
   private final String featureNamePrefix;
   private final Map<String, AtomicInteger> sequenceGenerator;

   public NameGenerator(ClassName defaultTarget) {
      this(defaultTarget, "");
   }

   public NameGenerator(ClassName defaultTarget, String featureNamePrefix) {
      this(defaultTarget, featureNamePrefix, new ConcurrentHashMap<>());
   }

   private NameGenerator(ClassName defaultTarget, String featureNamePrefix, Map<String, AtomicInteger> sequenceGenerator) {
      Assert.notNull(defaultTarget, "'defaultTarget' must not be null");
      this.defaultTarget = defaultTarget;
      this.featureNamePrefix = !StringUtils.hasText(featureNamePrefix) ? "" : featureNamePrefix;
      this.sequenceGenerator = sequenceGenerator;
   }

   String getFeatureNamePrefix() {
      return this.featureNamePrefix;
   }

   public ClassName generateClassName(String featureName, @Nullable ClassName target) {
      return this.generateUniqueName(
         this.getRootClassName(featureName, target), uniqueName -> ClassName.get(ClassUtils.getPackageName(uniqueName), ClassUtils.getShortName(uniqueName))
      );
   }

   private String getRootClassName(String featureName, @Nullable ClassName target) {
      Assert.hasLength(featureName, "'featureName' must not be empty");
      featureName = this.clean(featureName);
      ClassName targetToUse = target != null ? target : this.defaultTarget;
      String featureNameToUse = this.featureNamePrefix + featureName;
      return toName(targetToUse).replace("$", "_") + "__" + StringUtils.capitalize(featureNameToUse);
   }

   public String generateResourcePath(String extension, String featureName, @Nullable ClassName target) {
      Assert.hasLength(extension, "'extension' must not be empty");
      return this.generateUniqueName(this.getRootResourceName(featureName, target), uniqueName -> uniqueName + "." + extension);
   }

   private String getRootResourceName(String featureName, @Nullable ClassName target) {
      Assert.hasLength(featureName, "'featureName' must not be empty");
      ClassName targetToUse = target != null ? target : this.defaultTarget;
      String featureNameToUse = this.featureNamePrefix + (this.featureNamePrefix.isEmpty() ? "" : "-") + featureName;
      return toName(targetToUse).replace("$", "_").replace(".", "/") + "-" + featureNameToUse;
   }

   private String clean(String name) {
      StringBuilder clean = new StringBuilder();
      boolean lastNotLetter = true;

      for (char ch : name.toCharArray()) {
         if (!Character.isLetter(ch)) {
            lastNotLetter = true;
         } else {
            clean.append(lastNotLetter ? Character.toUpperCase(ch) : ch);
            lastNotLetter = false;
         }
      }

      return !clean.isEmpty() ? clean.toString() : "Aot";
   }

   private <T> T generateUniqueName(String name, Function<String, T> factory) {
      int sequence = this.sequenceGenerator.computeIfAbsent(name, key -> new AtomicInteger()).getAndIncrement();
      if (sequence > 0) {
         name = name + sequence;
      }

      return factory.apply(name);
   }

   NameGenerator withFeatureNamePrefix(String featureNamePrefix) {
      return new NameGenerator(this.defaultTarget, featureNamePrefix, this.sequenceGenerator);
   }

   private static String toName(ClassName className) {
      return GeneratedTypeReference.of(className).getName();
   }
}
