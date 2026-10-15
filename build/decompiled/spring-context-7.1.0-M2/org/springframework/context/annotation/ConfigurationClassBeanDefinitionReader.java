package org.springframework.context.annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.BeanRegistryAdapter;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

class ConfigurationClassBeanDefinitionReader {
   private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);
   private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();
   private final BeanDefinitionRegistry registry;
   private final SourceExtractor sourceExtractor;
   private final ResourceLoader resourceLoader;
   private final Environment environment;
   private final BeanNameGenerator importBeanNameGenerator;
   private final ImportRegistry importRegistry;
   private final ConditionEvaluator conditionEvaluator;

   ConfigurationClassBeanDefinitionReader(
      BeanDefinitionRegistry registry,
      SourceExtractor sourceExtractor,
      ResourceLoader resourceLoader,
      Environment environment,
      BeanNameGenerator importBeanNameGenerator,
      ImportRegistry importRegistry
   ) {
      this.registry = registry;
      this.sourceExtractor = sourceExtractor;
      this.resourceLoader = resourceLoader;
      this.environment = environment;
      this.importBeanNameGenerator = importBeanNameGenerator;
      this.importRegistry = importRegistry;
      this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
   }

   public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
      ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator trackedConditionEvaluator = new ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator(
         
      );

      for (ConfigurationClass configClass : configurationModel) {
         this.loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
      }
   }

   private void loadBeanDefinitionsForConfigurationClass(
      ConfigurationClass configClass, ConfigurationClassBeanDefinitionReader.TrackedConditionEvaluator trackedConditionEvaluator
   ) {
      if (trackedConditionEvaluator.shouldSkip(configClass)) {
         String beanName = configClass.getBeanName();
         if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
            this.registry.removeBeanDefinition(beanName);
         }

         this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
      } else {
         if (configClass.isImported()) {
            this.registerBeanDefinitionForImportedConfigurationClass(configClass);
         }

         for (BeanMethod beanMethod : configClass.getBeanMethods()) {
            this.loadBeanDefinitionsForBeanMethod(beanMethod);
         }

         this.loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
         this.loadBeanDefinitionsFromImportBeanDefinitionRegistrars(configClass.getImportBeanDefinitionRegistrars());
         this.loadBeanDefinitionsFromBeanRegistrars(configClass.getBeanRegistrars());
      }
   }

   private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
      AnnotationMetadata metadata = configClass.getMetadata();
      AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);
      ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
      configBeanDef.setScope(scopeMetadata.getScopeName());

      String configBeanName;
      try {
         configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
      } catch (IllegalArgumentException ex) {
         throw new IllegalStateException("Failed to generate bean name for imported class '" + configClass.getMetadata().getClassName() + "'", ex);
      }

      AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);
      BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
      definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
      this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
      configClass.setBeanName(configBeanName);
      if (logger.isTraceEnabled()) {
         logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
      }
   }

   private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
      ConfigurationClass configClass = beanMethod.getConfigurationClass();
      MethodMetadata metadata = beanMethod.getMetadata();
      String methodName = metadata.getMethodName();
      if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN)) {
         configClass.skippedBeanMethods.add(methodName);
      } else if (!configClass.skippedBeanMethods.contains(methodName)) {
         AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
         Assert.state(bean != null, "No @Bean annotation attributes");
         String[] explicitNames = bean.getStringArray("name");
         String beanName = explicitNames.length > 0 && StringUtils.hasText(explicitNames[0]) ? explicitNames[0] : null;
         String localBeanName = defaultBeanName(beanName, methodName);
         beanName = this.importBeanNameGenerator instanceof ConfigurationBeanNameGenerator cbng ? cbng.deriveBeanName(metadata, beanName) : localBeanName;
         if (!localBeanName.equals(beanName) && !this.registry.containsBeanDefinition(localBeanName) && !this.registry.isAlias(localBeanName)) {
            this.registry.registerAlias(beanName, localBeanName);
         }

         if (explicitNames.length > 0) {
            for (int i = 1; i < explicitNames.length; i++) {
               this.registry.registerAlias(beanName, explicitNames[i]);
            }
         }

         ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition(
            configClass, metadata, localBeanName
         );
         beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));
         if (this.isOverriddenByExistingDefinition(beanMethod, beanName, beanDef)) {
            if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
               throw new BeanDefinitionStoreException(
                  beanMethod.getConfigurationClass().getResource().getDescription(),
                  beanName,
                  "Bean name derived from @Bean method '"
                     + beanMethod.getMetadata().getMethodName()
                     + "' clashes with bean name for containing configuration class; please make those names unique!"
               );
            }
         } else {
            if (metadata.isStatic()) {
               if (configClass.getMetadata() instanceof StandardAnnotationMetadata sam) {
                  beanDef.setBeanClass(sam.getIntrospectedClass());
               } else {
                  beanDef.setBeanClassName(configClass.getMetadata().getClassName());
               }

               beanDef.setUniqueFactoryMethodName(methodName);
            } else {
               beanDef.setFactoryBeanName(configClass.getBeanName());
               beanDef.setUniqueFactoryMethodName(methodName);
            }

            if (metadata instanceof StandardMethodMetadata smm && configClass.getMetadata() instanceof StandardAnnotationMetadata sam) {
               Method method = ClassUtils.getMostSpecificMethod(smm.getIntrospectedMethod(), sam.getIntrospectedClass());
               if (method == smm.getIntrospectedMethod()) {
                  beanDef.setResolvedFactoryMethod(method);
               }
            }

            beanDef.setAutowireMode(3);
            AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);
            boolean autowireCandidate = bean.getBoolean("autowireCandidate");
            if (!autowireCandidate) {
               beanDef.setAutowireCandidate(false);
            }

            boolean defaultCandidate = bean.getBoolean("defaultCandidate");
            if (!defaultCandidate) {
               beanDef.setDefaultCandidate(false);
            }

            Bean.Bootstrap instantiation = (Bean.Bootstrap)bean.getEnum("bootstrap");
            if (instantiation == Bean.Bootstrap.BACKGROUND) {
               beanDef.setBackgroundInit(true);
            }

            String initMethodName = bean.getString("initMethod");
            if (StringUtils.hasText(initMethodName)) {
               beanDef.setInitMethodName(initMethodName);
            }

            String destroyMethodName = bean.getString("destroyMethod");
            beanDef.setDestroyMethodName(destroyMethodName);
            ScopedProxyMode proxyMode = ScopedProxyMode.NO;
            AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
            if (attributes != null) {
               beanDef.setScope(attributes.getString("value"));
               proxyMode = (ScopedProxyMode)attributes.getEnum("proxyMode");
               if (proxyMode == ScopedProxyMode.DEFAULT) {
                  proxyMode = ScopedProxyMode.NO;
               }
            }

            BeanDefinition beanDefToRegister = beanDef;
            if (proxyMode != ScopedProxyMode.NO) {
               BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
                  new BeanDefinitionHolder(beanDef, beanName), this.registry, proxyMode == ScopedProxyMode.TARGET_CLASS
               );
               beanDefToRegister = new ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition(
                  (RootBeanDefinition)proxyDef.getBeanDefinition(), configClass, metadata, localBeanName
               );
            }

            if (logger.isTraceEnabled()) {
               logger.trace(
                  "Registering bean definition for @Bean method %s.%s() with bean name '%s'"
                     .formatted(configClass.getMetadata().getClassName(), methodName, beanName)
               );
            }

            this.registry.registerBeanDefinition(beanName, beanDefToRegister);
         }
      }
   }

   private static String defaultBeanName(@Nullable String beanName, String methodName) {
      return beanName != null ? beanName : methodName;
   }

   private boolean isOverriddenByExistingDefinition(
      BeanMethod beanMethod, String beanName, ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition newBeanDef
   ) {
      if (!this.registry.containsBeanDefinition(beanName)) {
         return false;
      }

      BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);
      ConfigurationClass configClass = beanMethod.getConfigurationClass();
      if (!(existingBeanDef instanceof ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition ccbd)) {
         if (existingBeanDef instanceof ScannedGenericBeanDefinition scannedBeanDef) {
            if (beanMethod.getMetadata().getReturnTypeName().equals(scannedBeanDef.getBeanClassName())) {
               this.registry.removeBeanDefinition(beanName);
            }

            return false;
         } else {
            if (existingBeanDef.getRole() > 0) {
               return false;
            }

            if (!this.registry.isBeanDefinitionOverridable(beanName)) {
               throw new BeanDefinitionOverrideException(
                  beanName,
                  new ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition(configClass, beanMethod.getMetadata(), beanName),
                  existingBeanDef,
                  "@Bean definition illegally overridden by existing bean definition: " + existingBeanDef
               );
            }

            if (logger.isDebugEnabled()) {
               logger.debug(
                  "Skipping bean definition for %s: a definition for bean '%s' already exists. This top-level bean definition is considered as an override."
                     .formatted(beanMethod, beanName)
               );
            }

            return true;
         }
      } else if (!ccbd.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
         return false;
      } else if (ccbd.getFactoryMethodMetadata().getMethodName().equals(beanMethod.getMetadata().getMethodName())) {
         ccbd.setNonUniqueFactoryMethodName(ccbd.getFactoryMethodMetadata().getMethodName());
         return true;
      } else {
         Map<String, Object> attributes = configClass.getMetadata().getAnnotationAttributes(Configuration.class.getName());
         if ((attributes == null || !(Boolean)attributes.get("enforceUniqueMethods")) && this.registry.isBeanDefinitionOverridable(beanName)) {
            return true;
         } else {
            throw new BeanDefinitionOverrideException(
               beanName, newBeanDef, existingBeanDef, "@Bean method override with same bean name but different method name: " + existingBeanDef
            );
         }
      }
   }

   private void loadBeanDefinitionsFromImportedResources(Map<String, Class<? extends BeanDefinitionReader>> importedResources) {
      Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();
      importedResources.forEach((resource, readerClass) -> {
         if (BeanDefinitionReader.class == readerClass) {
            if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
               readerClass = GroovyBeanDefinitionReader.class;
            } else {
               readerClass = XmlBeanDefinitionReader.class;
            }
         }

         BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
         if (reader == null) {
            try {
               Constructor<? extends BeanDefinitionReader> constructor = readerClass.getDeclaredConstructor(BeanDefinitionRegistry.class);
               reader = (BeanDefinitionReader)BeanUtils.instantiateClass(constructor, new Object[]{this.registry});
               if (reader instanceof AbstractBeanDefinitionReader abdr) {
                  abdr.setResourceLoader(this.resourceLoader);
                  abdr.setEnvironment(this.environment);
               }

               readerInstanceCache.put((Class<?>)readerClass, reader);
            } catch (Throwable ex) {
               throw new IllegalStateException("Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]", ex);
            }
         }

         reader.loadBeanDefinitions(resource);
      });
   }

   private void loadBeanDefinitionsFromImportBeanDefinitionRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
      registrars.forEach((registrar, metadata) -> registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
   }

   private void loadBeanDefinitionsFromBeanRegistrars(MultiValueMap<String, BeanRegistrar> registrars) {
      registrars.values()
         .forEach(
            registrarList -> registrarList.forEach(
               registrar -> {
                  if (this.registry instanceof ListableBeanFactory beanFactory) {
                     registrar.register(new BeanRegistryAdapter(this.registry, beanFactory, this.environment, registrar.getClass()), this.environment);
                  } else {
                     throw new IllegalStateException(
                        "Cannot support bean registrars since " + this.registry.getClass().getName() + " does not implement ListableBeanFactory"
                     );
                  }
               }
            )
         );
   }

   private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {
      private final AnnotationMetadata annotationMetadata;
      private final MethodMetadata factoryMethodMetadata;
      private final String localBeanName;

      public ConfigurationClassBeanDefinition(ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String localBeanName) {
         this.annotationMetadata = configClass.getMetadata();
         this.factoryMethodMetadata = beanMethodMetadata;
         this.localBeanName = localBeanName;
         this.setResource(configClass.getResource());
         this.setLenientConstructorResolution(false);
      }

      public ConfigurationClassBeanDefinition(
         RootBeanDefinition original, ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String localBeanName
      ) {
         super(original);
         this.annotationMetadata = configClass.getMetadata();
         this.factoryMethodMetadata = beanMethodMetadata;
         this.localBeanName = localBeanName;
      }

      private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition original) {
         super(original);
         this.annotationMetadata = original.annotationMetadata;
         this.factoryMethodMetadata = original.factoryMethodMetadata;
         this.localBeanName = original.localBeanName;
      }

      public AnnotationMetadata getMetadata() {
         return this.annotationMetadata;
      }

      public MethodMetadata getFactoryMethodMetadata() {
         return this.factoryMethodMetadata;
      }

      public boolean isFactoryMethod(Method candidate) {
         return super.isFactoryMethod(candidate)
            && BeanAnnotationHelper.isBeanAnnotated(candidate)
            && BeanAnnotationHelper.determineBeanNameFor(candidate).equals(this.localBeanName);
      }

      public ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition cloneBeanDefinition() {
         return new ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition(this);
      }
   }

   private class TrackedConditionEvaluator {
      private final Map<ConfigurationClass, Boolean> skipped = new HashMap<>();

      public boolean shouldSkip(ConfigurationClass configClass) {
         Boolean skip = this.skipped.get(configClass);
         if (skip == null) {
            if (configClass.isImported()) {
               boolean allSkipped = true;

               for (ConfigurationClass importedBy : configClass.getImportedBy()) {
                  if (!this.shouldSkip(importedBy)) {
                     allSkipped = false;
                     break;
                  }
               }

               if (allSkipped) {
                  skip = true;
               }
            }

            if (skip == null) {
               skip = ConfigurationClassBeanDefinitionReader.this.conditionEvaluator
                  .shouldSkip(configClass.getMetadata(), ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
            }

            this.skipped.put(configClass, skip);
         }

         return skip;
      }
   }
}
