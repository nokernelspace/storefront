package org.springframework.aot.generate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.TypeSpec;
import org.springframework.util.Assert;

public class GeneratedClasses {
   private final NameGenerator nameGenerator;
   private final List<GeneratedClass> classes;
   private final Map<GeneratedClasses.Owner, GeneratedClass> classesByOwner;

   GeneratedClasses(NameGenerator nameGenerator) {
      this(nameGenerator, new ArrayList<>(), new ConcurrentHashMap<>());
   }

   private GeneratedClasses(NameGenerator nameGenerator, List<GeneratedClass> classes, Map<GeneratedClasses.Owner, GeneratedClass> classesByOwner) {
      Assert.notNull(nameGenerator, "'nameGenerator' must not be null");
      this.nameGenerator = nameGenerator;
      this.classes = classes;
      this.classesByOwner = classesByOwner;
   }

   public GeneratedClass getOrAddForFeature(String featureName, Consumer<TypeSpec.Builder> type) {
      Assert.hasLength(featureName, "'featureName' must not be empty");
      Assert.notNull(type, "'type' must not be null");
      GeneratedClasses.Owner owner = new GeneratedClasses.Owner(this.nameGenerator.getFeatureNamePrefix(), featureName, null);
      GeneratedClass generatedClass = this.classesByOwner.computeIfAbsent(owner, key -> this.createAndAddGeneratedClass(featureName, null, type));
      generatedClass.assertSameType(type);
      return generatedClass;
   }

   public GeneratedClass getOrAddForFeatureComponent(String featureName, ClassName targetComponent, Consumer<TypeSpec.Builder> type) {
      Assert.hasLength(featureName, "'featureName' must not be empty");
      Assert.notNull(targetComponent, "'targetComponent' must not be null");
      Assert.notNull(type, "'type' must not be null");
      GeneratedClasses.Owner owner = new GeneratedClasses.Owner(this.nameGenerator.getFeatureNamePrefix(), featureName, targetComponent);
      GeneratedClass generatedClass = this.classesByOwner.computeIfAbsent(owner, key -> this.createAndAddGeneratedClass(featureName, targetComponent, type));
      generatedClass.assertSameType(type);
      return generatedClass;
   }

   public GeneratedClass getOrAddForFeatureComponent(String featureName, Class<?> targetComponent, Consumer<TypeSpec.Builder> type) {
      return this.getOrAddForFeatureComponent(featureName, ClassName.get(targetComponent), type);
   }

   public GeneratedClass addForFeature(String featureName, Consumer<TypeSpec.Builder> type) {
      Assert.hasLength(featureName, "'featureName' must not be empty");
      Assert.notNull(type, "'type' must not be null");
      return this.createAndAddGeneratedClass(featureName, null, type);
   }

   public GeneratedClass addForFeatureComponent(String featureName, ClassName targetComponent, Consumer<TypeSpec.Builder> type) {
      Assert.hasLength(featureName, "'featureName' must not be empty");
      Assert.notNull(targetComponent, "'targetComponent' must not be null");
      Assert.notNull(type, "'type' must not be null");
      return this.createAndAddGeneratedClass(featureName, targetComponent, type);
   }

   public GeneratedClass addForFeatureComponent(String featureName, Class<?> targetComponent, Consumer<TypeSpec.Builder> type) {
      return this.addForFeatureComponent(featureName, ClassName.get(targetComponent), type);
   }

   private GeneratedClass createAndAddGeneratedClass(String featureName, @Nullable ClassName targetComponent, Consumer<TypeSpec.Builder> type) {
      ClassName className = this.nameGenerator.generateClassName(featureName, targetComponent);
      GeneratedClass generatedClass = new GeneratedClass(className, type);
      this.classes.add(generatedClass);
      return generatedClass;
   }

   void writeTo(GeneratedFiles generatedFiles) {
      Assert.notNull(generatedFiles, "'generatedFiles' must not be null");
      List<GeneratedClass> generatedClasses = new ArrayList<>(this.classes);
      generatedClasses.sort(Comparator.comparing(GeneratedClass::getName));

      for (GeneratedClass generatedClass : generatedClasses) {
         generatedFiles.addSourceFile(generatedClass.generateJavaFile());
      }
   }

   GeneratedClasses withFeatureNamePrefix(String featureNamePrefix) {
      return new GeneratedClasses(this.nameGenerator.withFeatureNamePrefix(featureNamePrefix), this.classes, this.classesByOwner);
   }

   private record Owner(String featureNamePrefix, String featureName, @Nullable ClassName target) {
   }
}
