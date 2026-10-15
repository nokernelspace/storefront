package org.springframework.context.annotation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

final class ConfigurationClass {
   private final AnnotationMetadata metadata;
   private final Resource resource;
   private @Nullable String beanName;
   private boolean scanned = false;
   private final Set<ConfigurationClass> importedBy = new LinkedHashSet<>(1);
   private final Set<BeanMethod> beanMethods = new LinkedHashSet<>();
   private final Map<String, Class<? extends BeanDefinitionReader>> importedResources = new LinkedHashMap<>();
   private final MultiValueMap<String, BeanRegistrar> beanRegistrars = new LinkedMultiValueMap();
   private final Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> importBeanDefinitionRegistrars = new LinkedHashMap<>();
   final Set<String> skippedBeanMethods = new HashSet<>();

   ConfigurationClass(MetadataReader metadataReader, String beanName) {
      Assert.notNull(beanName, "Bean name must not be null");
      this.metadata = metadataReader.getAnnotationMetadata();
      this.resource = metadataReader.getResource();
      this.beanName = beanName;
   }

   ConfigurationClass(MetadataReader metadataReader, ConfigurationClass importedBy) {
      this.metadata = metadataReader.getAnnotationMetadata();
      this.resource = metadataReader.getResource();
      this.importedBy.add(importedBy);
   }

   ConfigurationClass(Class<?> clazz, String beanName) {
      Assert.notNull(beanName, "Bean name must not be null");
      this.metadata = AnnotationMetadata.introspect(clazz);
      this.resource = new DescriptiveResource(clazz.getName());
      this.beanName = beanName;
   }

   ConfigurationClass(Class<?> clazz, ConfigurationClass importedBy) {
      this.metadata = AnnotationMetadata.introspect(clazz);
      this.resource = new DescriptiveResource(clazz.getName());
      this.importedBy.add(importedBy);
   }

   ConfigurationClass(AnnotationMetadata metadata, String beanName, boolean scanned) {
      Assert.notNull(beanName, "Bean name must not be null");
      this.metadata = metadata;
      this.resource = new DescriptiveResource(metadata.getClassName());
      this.beanName = beanName;
      this.scanned = scanned;
   }

   AnnotationMetadata getMetadata() {
      return this.metadata;
   }

   Resource getResource() {
      return this.resource;
   }

   String getSimpleName() {
      return ClassUtils.getShortName(this.getMetadata().getClassName());
   }

   void setBeanName(@Nullable String beanName) {
      this.beanName = beanName;
   }

   @Nullable String getBeanName() {
      return this.beanName;
   }

   boolean isScanned() {
      return this.scanned;
   }

   boolean isImported() {
      return !this.importedBy.isEmpty();
   }

   void mergeImportedBy(ConfigurationClass otherConfigClass) {
      this.importedBy.addAll(otherConfigClass.importedBy);
   }

   Set<ConfigurationClass> getImportedBy() {
      return this.importedBy;
   }

   void addBeanMethod(BeanMethod method) {
      this.beanMethods.add(method);
   }

   Set<BeanMethod> getBeanMethods() {
      return this.beanMethods;
   }

   boolean hasNonStaticBeanMethods() {
      for (BeanMethod beanMethod : this.beanMethods) {
         if (!beanMethod.getMetadata().isStatic()) {
            return true;
         }
      }

      return false;
   }

   void addImportedResource(String importedResource, Class<? extends BeanDefinitionReader> readerClass) {
      this.importedResources.put(importedResource, readerClass);
   }

   Map<String, Class<? extends BeanDefinitionReader>> getImportedResources() {
      return this.importedResources;
   }

   void addBeanRegistrar(String sourceClassName, BeanRegistrar beanRegistrar) {
      this.beanRegistrars.add(sourceClassName, beanRegistrar);
   }

   public MultiValueMap<String, BeanRegistrar> getBeanRegistrars() {
      return this.beanRegistrars;
   }

   void addImportBeanDefinitionRegistrar(ImportBeanDefinitionRegistrar registrar, AnnotationMetadata importingClassMetadata) {
      this.importBeanDefinitionRegistrars.put(registrar, importingClassMetadata);
   }

   Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> getImportBeanDefinitionRegistrars() {
      return this.importBeanDefinitionRegistrars;
   }

   void validate(ProblemReporter problemReporter) {
      Map<String, Object> attributes = this.metadata.getAnnotationAttributes(Configuration.class.getName());
      if (attributes != null && (Boolean)attributes.get("proxyBeanMethods") && this.hasNonStaticBeanMethods() && this.metadata.isFinal()) {
         problemReporter.error(new ConfigurationClass.FinalConfigurationProblem());
      }

      for (BeanMethod beanMethod : this.beanMethods) {
         beanMethod.validate(problemReporter);
      }

      if (attributes != null && (Boolean)attributes.get("enforceUniqueMethods")) {
         Map<String, MethodMetadata> beanMethodsByName = new LinkedHashMap<>();

         for (BeanMethod beanMethod : this.beanMethods) {
            MethodMetadata current = beanMethod.getMetadata();
            MethodMetadata existing = beanMethodsByName.put(current.getMethodName(), current);
            if (existing != null && existing.getDeclaringClassName().equals(current.getDeclaringClassName())) {
               problemReporter.error(new ConfigurationClass.BeanMethodOverloadingProblem(existing.getMethodName()));
            }
         }
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof ConfigurationClass that && this.getMetadata().getClassName().equals(that.getMetadata().getClassName());
   }

   @Override
   public int hashCode() {
      return this.getMetadata().getClassName().hashCode();
   }

   @Override
   public String toString() {
      return "ConfigurationClass: beanName '" + this.beanName + "', " + this.resource;
   }

   private class BeanMethodOverloadingProblem extends Problem {
      BeanMethodOverloadingProblem(String methodName) {
         super(
            String.format(
               "@Configuration class '%s' contains overloaded @Bean methods with name '%s'. Use unique method names for separate bean definitions (with individual conditions etc) or switch '@Configuration.enforceUniqueMethods' to 'false'.",
               ConfigurationClass.this.getSimpleName(),
               methodName
            ),
            new Location(ConfigurationClass.this.getResource(), ConfigurationClass.this.getMetadata())
         );
      }
   }

   private class FinalConfigurationProblem extends Problem {
      FinalConfigurationProblem() {
         super(
            String.format("@Configuration class '%s' may not be final. Remove the final modifier to continue.", ConfigurationClass.this.getSimpleName()),
            new Location(ConfigurationClass.this.getResource(), ConfigurationClass.this.getMetadata())
         );
      }
   }
}
