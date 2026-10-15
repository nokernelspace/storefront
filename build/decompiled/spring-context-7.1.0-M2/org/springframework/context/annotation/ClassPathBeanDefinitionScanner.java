package org.springframework.context.annotation;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;

public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {
   private final BeanDefinitionRegistry registry;
   private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();
   private String @Nullable [] autowireCandidatePatterns;
   private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;
   private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();
   private boolean includeAnnotationConfig = true;

   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
      this(registry, true);
   }

   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
      this(registry, useDefaultFilters, getOrCreateEnvironment(registry));
   }

   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters, Environment environment) {
      this(registry, useDefaultFilters, environment, registry instanceof ResourceLoader resourceLoader ? resourceLoader : null);
   }

   public ClassPathBeanDefinitionScanner(
      BeanDefinitionRegistry registry, boolean useDefaultFilters, Environment environment, @Nullable ResourceLoader resourceLoader
   ) {
      Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
      this.registry = registry;
      if (useDefaultFilters) {
         this.registerDefaultFilters();
      }

      this.setEnvironment(environment);
      this.setResourceLoader(resourceLoader);
   }

   @Override
   public final BeanDefinitionRegistry getRegistry() {
      return this.registry;
   }

   public void setBeanDefinitionDefaults(@Nullable BeanDefinitionDefaults beanDefinitionDefaults) {
      this.beanDefinitionDefaults = beanDefinitionDefaults != null ? beanDefinitionDefaults : new BeanDefinitionDefaults();
   }

   public BeanDefinitionDefaults getBeanDefinitionDefaults() {
      return this.beanDefinitionDefaults;
   }

   public void setAutowireCandidatePatterns(String @Nullable ... autowireCandidatePatterns) {
      this.autowireCandidatePatterns = autowireCandidatePatterns;
   }

   public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
      this.beanNameGenerator = beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE;
   }

   public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
      this.scopeMetadataResolver = scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver();
   }

   public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
      this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(scopedProxyMode);
   }

   public void setIncludeAnnotationConfig(boolean includeAnnotationConfig) {
      this.includeAnnotationConfig = includeAnnotationConfig;
   }

   public int scan(String... basePackages) {
      int beanCountAtScanStart = this.registry.getBeanDefinitionCount();
      this.doScan(basePackages);
      if (this.includeAnnotationConfig) {
         AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
      }

      return this.registry.getBeanDefinitionCount() - beanCountAtScanStart;
   }

   protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
      Assert.notEmpty(basePackages, "At least one base package must be specified");
      Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();

      for (String basePackage : basePackages) {
         for (BeanDefinition candidate : this.findCandidateComponents(basePackage)) {
            ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
            candidate.setScope(scopeMetadata.getScopeName());
            String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
            if (candidate instanceof AbstractBeanDefinition abstractBeanDefinition) {
               this.postProcessBeanDefinition(abstractBeanDefinition, beanName);
            }

            if (candidate instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
               AnnotationConfigUtils.processCommonDefinitionAnnotations(annotatedBeanDefinition);
            }

            if (this.checkCandidate(beanName, candidate)) {
               BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
               definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
               beanDefinitions.add(definitionHolder);
               this.registerBeanDefinition(definitionHolder, this.registry);
            }
         }
      }

      return beanDefinitions;
   }

   protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
      beanDefinition.applyDefaults(this.beanDefinitionDefaults);
      if (this.autowireCandidatePatterns != null) {
         beanDefinition.setAutowireCandidate(PatternMatchUtils.simpleMatch(this.autowireCandidatePatterns, beanName));
      }
   }

   protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
      BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
   }

   protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
      if (!this.registry.containsBeanDefinition(beanName)) {
         return true;
      }

      BeanDefinition existingDef = this.registry.getBeanDefinition(beanName);
      BeanDefinition originatingDef = existingDef.getOriginatingBeanDefinition();
      if (originatingDef != null) {
         existingDef = originatingDef;
      }

      if (existingDef instanceof ScannedGenericBeanDefinition
         || !this.registry.isBeanDefinitionOverridable(beanName)
            && !ObjectUtils.nullSafeEquals(beanDefinition.getBeanClassName(), existingDef.getBeanClassName())) {
         if (this.isCompatible(beanDefinition, existingDef)) {
            return false;
         } else {
            throw new ConflictingBeanDefinitionException(
               "Annotation-specified bean name '"
                  + beanName
                  + "' for bean class ["
                  + beanDefinition.getBeanClassName()
                  + "] conflicts with existing, non-compatible bean definition of same name and class ["
                  + existingDef.getBeanClassName()
                  + "]"
            );
         }
      } else {
         return false;
      }
   }

   protected boolean isCompatible(BeanDefinition newDef, BeanDefinition existingDef) {
      return newDef.getSource() != null && newDef.getSource().equals(existingDef.getSource()) || newDef.equals(existingDef);
   }

   private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
      Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
      return (Environment)(registry instanceof EnvironmentCapable environmentCapable ? environmentCapable.getEnvironment() : new StandardEnvironment());
   }
}
