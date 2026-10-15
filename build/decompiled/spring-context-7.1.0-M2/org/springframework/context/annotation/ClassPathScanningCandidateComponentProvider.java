package org.springframework.context.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.index.CandidateComponentsIndex;
import org.springframework.context.index.CandidateComponentsIndexLoader;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.ClassFormatException;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Indexed;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class ClassPathScanningCandidateComponentProvider implements EnvironmentCapable, ResourceLoaderAware {
   static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";
   public static final String IGNORE_CLASSFORMAT_PROPERTY_NAME = "spring.classformat.ignore";
   private static final boolean shouldIgnoreClassFormatException = SpringProperties.getFlag("spring.classformat.ignore");
   protected final Log logger = LogFactory.getLog(this.getClass());
   private String resourcePattern = "**/*.class";
   private final List<TypeFilter> includeFilters = new ArrayList<>();
   private final List<TypeFilter> excludeFilters = new ArrayList<>();
   private @Nullable Environment environment;
   private @Nullable ConditionEvaluator conditionEvaluator;
   private @Nullable ResourcePatternResolver resourcePatternResolver;
   private @Nullable MetadataReaderFactory metadataReaderFactory;
   private @Nullable CandidateComponentsIndex componentsIndex;

   protected ClassPathScanningCandidateComponentProvider() {
   }

   public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters) {
      this(useDefaultFilters, new StandardEnvironment());
   }

   public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters, Environment environment) {
      if (useDefaultFilters) {
         this.registerDefaultFilters();
      }

      this.setEnvironment(environment);
      this.setResourceLoader(null);
   }

   public void setResourcePattern(String resourcePattern) {
      Assert.notNull(resourcePattern, "'resourcePattern' must not be null");
      this.resourcePattern = resourcePattern;
   }

   public void addIncludeFilter(TypeFilter includeFilter) {
      this.includeFilters.add(includeFilter);
   }

   public void addExcludeFilter(TypeFilter excludeFilter) {
      this.excludeFilters.add(0, excludeFilter);
   }

   public void resetFilters(boolean useDefaultFilters) {
      this.includeFilters.clear();
      this.excludeFilters.clear();
      if (useDefaultFilters) {
         this.registerDefaultFilters();
      }
   }

   protected void registerDefaultFilters() {
      this.includeFilters.add(new AnnotationTypeFilter(Component.class));
      ClassLoader cl = ClassPathScanningCandidateComponentProvider.class.getClassLoader();

      try {
         this.includeFilters.add(new AnnotationTypeFilter(ClassUtils.forName("jakarta.inject.Named", cl), false));
         this.logger.trace("JSR-330 'jakarta.inject.Named' annotation found and supported for component scanning");
      } catch (ClassNotFoundException var3) {
      }
   }

   public void setEnvironment(Environment environment) {
      Assert.notNull(environment, "Environment must not be null");
      this.environment = environment;
      this.conditionEvaluator = null;
   }

   public final Environment getEnvironment() {
      if (this.environment == null) {
         this.environment = new StandardEnvironment();
      }

      return this.environment;
   }

   protected @Nullable BeanDefinitionRegistry getRegistry() {
      return null;
   }

   @Override
   public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
      this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
      this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
      this.componentsIndex = CandidateComponentsIndexLoader.loadIndex(this.resourcePatternResolver.getClassLoader());
   }

   public final ResourceLoader getResourceLoader() {
      return this.getResourcePatternResolver();
   }

   private ResourcePatternResolver getResourcePatternResolver() {
      if (this.resourcePatternResolver == null) {
         this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
      }

      return this.resourcePatternResolver;
   }

   public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
      this.metadataReaderFactory = metadataReaderFactory;
   }

   public final MetadataReaderFactory getMetadataReaderFactory() {
      if (this.metadataReaderFactory == null) {
         this.metadataReaderFactory = new CachingMetadataReaderFactory();
      }

      return this.metadataReaderFactory;
   }

   public Set<BeanDefinition> findCandidateComponents(String basePackage) {
      if (this.componentsIndex != null && this.indexSupportsIncludeFilters()) {
         if (this.componentsIndex.hasScannedPackage(basePackage)) {
            return this.addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
         }

         this.componentsIndex.registerScan(basePackage);
      }

      return this.scanCandidateComponents(basePackage);
   }

   private boolean indexSupportsIncludeFilters() {
      for (TypeFilter includeFilter : this.includeFilters) {
         if (!this.indexSupportsIncludeFilter(includeFilter)) {
            return false;
         }
      }

      return true;
   }

   private boolean indexSupportsIncludeFilter(TypeFilter filter) {
      if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
         Class<? extends Annotation> annotationType = annotationTypeFilter.getAnnotationType();
         return this.isStereotypeAnnotationForIndex(annotationType);
      } else if (filter instanceof AssignableTypeFilter assignableTypeFilter) {
         Class<?> target = assignableTypeFilter.getTargetType();
         return AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, target);
      } else {
         return false;
      }
   }

   private void registerCandidateTypeForIncludeFilter(String className, TypeFilter filter) {
      if (this.componentsIndex != null) {
         if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
            Class<? extends Annotation> annotationType = annotationTypeFilter.getAnnotationType();
            if (this.isStereotypeAnnotationForIndex(annotationType)) {
               this.componentsIndex.registerCandidateType(className, annotationType.getName());
            }
         } else if (filter instanceof AssignableTypeFilter assignableTypeFilter) {
            Class<?> target = assignableTypeFilter.getTargetType();
            if (AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, target)) {
               this.componentsIndex.registerCandidateType(className, target.getName());
            }
         }
      }
   }

   private @Nullable String extractStereotype(TypeFilter filter) {
      if (filter instanceof AnnotationTypeFilter annotationTypeFilter) {
         return annotationTypeFilter.getAnnotationType().getName();
      } else {
         return filter instanceof AssignableTypeFilter assignableTypeFilter ? assignableTypeFilter.getTargetType().getName() : null;
      }
   }

   private boolean isStereotypeAnnotationForIndex(Class<? extends Annotation> annotationType) {
      return AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, annotationType) || annotationType.getName().startsWith("jakarta.");
   }

   private Set<BeanDefinition> addCandidateComponentsFromIndex(CandidateComponentsIndex index, String basePackage) {
      Set<BeanDefinition> candidates = new LinkedHashSet<>();

      try {
         Set<String> types = new HashSet<>();

         for (TypeFilter filter : this.includeFilters) {
            String stereotype = this.extractStereotype(filter);
            if (stereotype == null) {
               throw new IllegalArgumentException("Failed to extract stereotype from " + filter);
            }

            types.addAll(index.getCandidateTypes(basePackage, stereotype));
         }

         boolean traceEnabled = this.logger.isTraceEnabled();
         boolean debugEnabled = this.logger.isDebugEnabled();

         for (String type : types) {
            MetadataReader metadataReader = this.getMetadataReaderFactory().getMetadataReader(type);
            if (this.isCandidateComponent(metadataReader)) {
               ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
               sbd.setSource(metadataReader.getResource());
               if (this.isCandidateComponent(sbd)) {
                  if (debugEnabled) {
                     this.logger.debug("Using candidate component class from index: " + type);
                  }

                  candidates.add(sbd);
               } else if (debugEnabled) {
                  this.logger.debug("Ignored because not a concrete top-level class: " + type);
               }
            } else if (traceEnabled) {
               this.logger.trace("Ignored because matching an exclude filter: " + type);
            }
         }

         return candidates;
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
      }
   }

   private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
      Set<BeanDefinition> candidates = new LinkedHashSet<>();

      try {
         String packageSearchPattern = "classpath*:" + this.resolveBasePackage(basePackage) + "/" + this.resourcePattern;
         Resource[] resources = this.getResourcePatternResolver().getResources(packageSearchPattern);
         boolean traceEnabled = this.logger.isTraceEnabled();
         boolean debugEnabled = this.logger.isDebugEnabled();

         for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.contains("$$")) {
               if (traceEnabled) {
                  this.logger.trace("Scanning " + resource);
               }

               try {
                  MetadataReader metadataReader = this.getMetadataReaderFactory().getMetadataReader(resource);
                  if (this.isCandidateComponent(metadataReader)) {
                     ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
                     sbd.setSource(resource);
                     if (this.isCandidateComponent(sbd)) {
                        if (debugEnabled) {
                           this.logger.debug("Identified candidate component class: " + resource);
                        }

                        candidates.add(sbd);
                     } else if (debugEnabled) {
                        this.logger.debug("Ignored because not a concrete top-level class: " + resource);
                     }
                  } else if (traceEnabled) {
                     this.logger.trace("Ignored because not matching any filter: " + resource);
                  }
               } catch (FileNotFoundException ex) {
                  if (traceEnabled) {
                     this.logger.trace("Ignored non-readable " + resource + ": " + ex.getMessage());
                  }
               } catch (ClassFormatException ex) {
                  if (!shouldIgnoreClassFormatException) {
                     throw new BeanDefinitionStoreException(
                        "Incompatible class format in "
                           + resource
                           + ": set system property 'spring.classformat.ignore' to 'true' if you mean to ignore such files during classpath scanning",
                        ex
                     );
                  }

                  if (debugEnabled) {
                     this.logger.debug("Ignored incompatible class format in " + resource + ": " + ex.getMessage());
                  }
               } catch (Throwable ex) {
                  throw new BeanDefinitionStoreException("Failed to read candidate component class: " + resource, ex);
               }
            }
         }

         return candidates;
      } catch (IOException ex) {
         throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
      }
   }

   protected String resolveBasePackage(String basePackage) {
      return ClassUtils.convertClassNameToResourcePath(this.getEnvironment().resolveRequiredPlaceholders(basePackage));
   }

   protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
      for (TypeFilter filter : this.excludeFilters) {
         if (filter.match(metadataReader, this.getMetadataReaderFactory())) {
            return false;
         }
      }

      for (TypeFilter filter : this.includeFilters) {
         if (filter.match(metadataReader, this.getMetadataReaderFactory())) {
            this.registerCandidateTypeForIncludeFilter(metadataReader.getClassMetadata().getClassName(), filter);
            return this.isConditionMatch(metadataReader);
         }
      }

      return false;
   }

   private boolean isConditionMatch(MetadataReader metadataReader) {
      if (this.conditionEvaluator == null) {
         this.conditionEvaluator = new ConditionEvaluator(this.getRegistry(), this.environment, this.resourcePatternResolver);
      }

      return !this.conditionEvaluator.shouldSkip(metadataReader.getAnnotationMetadata());
   }

   protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
      AnnotationMetadata metadata = beanDefinition.getMetadata();
      return metadata.isIndependent() && (metadata.isConcrete() || metadata.isAbstract() && metadata.hasAnnotatedMethods(Lookup.class.getName()));
   }

   public void clearCache() {
      if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory cmrf) {
         cmrf.clearCache();
      }
   }
}
