package org.springframework.context.annotation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.lang.model.element.Modifier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aot.generate.AccessControl;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.generate.AccessControl.Visibility;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.aot.InstanceSupplierCodeGenerator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.BeanRegistryAdapter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.RegisteredBean.InstantiationDescriptor;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertySourceDescriptor;
import org.springframework.core.io.support.PropertySourceProcessor;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.NameAllocator;
import org.springframework.javapoet.ParameterizedTypeName;
import org.springframework.javapoet.MethodSpec.Builder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class ConfigurationClassPostProcessor
   implements BeanDefinitionRegistryPostProcessor,
   BeanRegistrationAotProcessor,
   BeanFactoryInitializationAotProcessor,
   PriorityOrdered,
   ResourceLoaderAware,
   ApplicationStartupAware,
   BeanClassLoaderAware,
   EnvironmentAware {
   public static final AnnotationBeanNameGenerator IMPORT_BEAN_NAME_GENERATOR = FullyQualifiedAnnotationBeanNameGenerator.INSTANCE;
   private static final String IMPORT_REGISTRY_BEAN_NAME = ConfigurationClassPostProcessor.class.getName() + ".importRegistry";
   private final Log logger = LogFactory.getLog(this.getClass());
   private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();
   private ProblemReporter problemReporter = new FailFastProblemReporter();
   private @Nullable Environment environment;
   private ResourceLoader resourceLoader = new DefaultResourceLoader();
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();
   private boolean setMetadataReaderFactoryCalled = false;
   private final Set<Integer> registriesPostProcessed = new HashSet<>();
   private final Set<Integer> factoriesPostProcessed = new HashSet<>();
   private @Nullable ConfigurationClassBeanDefinitionReader reader;
   private boolean localBeanNameGeneratorSet = false;
   private BeanNameGenerator componentScanBeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;
   private BeanNameGenerator importBeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR;
   private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
   private List<PropertySourceDescriptor> propertySourceDescriptors = Collections.emptyList();
   private final MultiValueMap<String, BeanRegistrar> beanRegistrars = new LinkedMultiValueMap();

   public int getOrder() {
      return Integer.MAX_VALUE;
   }

   public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
      this.sourceExtractor = (SourceExtractor)(sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
   }

   public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
      this.problemReporter = (ProblemReporter)(problemReporter != null ? problemReporter : new FailFastProblemReporter());
   }

   public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
      Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
      this.metadataReaderFactory = metadataReaderFactory;
      this.setMetadataReaderFactoryCalled = true;
   }

   public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
      Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
      this.localBeanNameGeneratorSet = true;
      this.componentScanBeanNameGenerator = beanNameGenerator;
      this.importBeanNameGenerator = beanNameGenerator;
   }

   @Override
   public void setEnvironment(Environment environment) {
      Assert.notNull(environment, "Environment must not be null");
      this.environment = environment;
   }

   @Override
   public void setResourceLoader(ResourceLoader resourceLoader) {
      Assert.notNull(resourceLoader, "ResourceLoader must not be null");
      this.resourceLoader = resourceLoader;
      if (!this.setMetadataReaderFactoryCalled) {
         this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
      }
   }

   public void setBeanClassLoader(ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
      if (!this.setMetadataReaderFactoryCalled) {
         this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
      }
   }

   @Override
   public void setApplicationStartup(ApplicationStartup applicationStartup) {
      this.applicationStartup = applicationStartup;
   }

   public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
      int registryId = System.identityHashCode(registry);
      if (this.registriesPostProcessed.contains(registryId)) {
         throw new IllegalStateException("postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
      }

      if (this.factoriesPostProcessed.contains(registryId)) {
         throw new IllegalStateException("postProcessBeanFactory already called on this post-processor against " + registry);
      }

      this.registriesPostProcessed.add(registryId);
      this.processConfigBeanDefinitions(registry);
   }

   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      int factoryId = System.identityHashCode(beanFactory);
      if (this.factoriesPostProcessed.contains(factoryId)) {
         throw new IllegalStateException("postProcessBeanFactory already called on this post-processor against " + beanFactory);
      }

      this.factoriesPostProcessed.add(factoryId);
      if (!this.registriesPostProcessed.contains(factoryId)) {
         this.processConfigBeanDefinitions((BeanDefinitionRegistry)beanFactory);
      }

      this.enhanceConfigurationClasses(beanFactory);
      beanFactory.addBeanPostProcessor(new ConfigurationClassPostProcessor.ImportAwareBeanPostProcessor(beanFactory));
   }

   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      Object configClassAttr = registeredBean.getMergedBeanDefinition().getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
      return "full".equals(configClassAttr)
         ? BeanRegistrationAotContribution.withCustomCodeFragments(
            codeFragments -> new ConfigurationClassPostProcessor.ConfigurationClassProxyBeanRegistrationCodeFragments(codeFragments, registeredBean)
         )
         : null;
   }

   public @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
      boolean hasPropertySourceDescriptors = !CollectionUtils.isEmpty(this.propertySourceDescriptors);
      boolean hasImportRegistry = beanFactory.containsBean(IMPORT_REGISTRY_BEAN_NAME);
      boolean hasBeanRegistrars = !this.beanRegistrars.isEmpty();
      return !hasPropertySourceDescriptors && !hasImportRegistry && !hasBeanRegistrars
         ? null
         : (generationContext, code) -> {
            if (hasPropertySourceDescriptors) {
               new ConfigurationClassPostProcessor.PropertySourcesAotContribution(this.propertySourceDescriptors, this::resolvePropertySourceLocation)
                  .applyTo(generationContext, code);
            }

            if (hasImportRegistry) {
               new ConfigurationClassPostProcessor.ImportAwareAotContribution(beanFactory).applyTo(generationContext, code);
            }

            if (hasBeanRegistrars) {
               new ConfigurationClassPostProcessor.BeanRegistrarAotContribution(this.beanRegistrars, beanFactory).applyTo(generationContext, code);
            }
         };
   }

   private @Nullable Resource resolvePropertySourceLocation(String location) {
      try {
         String resolvedLocation = this.environment != null ? this.environment.resolveRequiredPlaceholders(location) : location;
         return this.resourceLoader.getResource(resolvedLocation);
      } catch (Exception ex) {
         return null;
      }
   }

   public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
      List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
      String[] candidateNames = registry.getBeanDefinitionNames();

      for (String beanName : candidateNames) {
         BeanDefinition beanDef = registry.getBeanDefinition(beanName);
         if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
            }
         } else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
            configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
         }
      }

      if (!configCandidates.isEmpty()) {
         configCandidates.sort((bd1, bd2) -> {
            int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
            int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
            return Integer.compare(i1, i2);
         });
         SingletonBeanRegistry singletonRegistry = null;
         if (registry instanceof SingletonBeanRegistry sbr) {
            singletonRegistry = sbr;
            BeanNameGenerator configurationGenerator = (BeanNameGenerator)singletonRegistry.getSingleton(
               "org.springframework.context.annotation.internalConfigurationBeanNameGenerator"
            );
            if (configurationGenerator != null) {
               if (this.localBeanNameGeneratorSet) {
                  if (configurationGenerator instanceof ConfigurationBeanNameGenerator & configurationGenerator != this.importBeanNameGenerator) {
                     throw new IllegalStateException(
                        "Context-level ConfigurationBeanNameGenerator ["
                           + configurationGenerator
                           + "] must not be overridden with processor-level generator ["
                           + this.importBeanNameGenerator
                           + "]"
                     );
                  }
               } else {
                  this.componentScanBeanNameGenerator = configurationGenerator;
                  this.importBeanNameGenerator = configurationGenerator;
               }
            }
         }

         if (this.environment == null) {
            this.environment = new StandardEnvironment();
         }

         ConfigurationClassParser parser = new ConfigurationClassParser(
            this.metadataReaderFactory, this.problemReporter, this.environment, this.resourceLoader, this.componentScanBeanNameGenerator, registry
         );
         Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
         Set<ConfigurationClass> alreadyParsed = CollectionUtils.newHashSet(configCandidates.size());

         do {
            StartupStep processConfig = this.applicationStartup.start("spring.context.config-classes.parse");
            parser.parse(candidates);
            parser.validate();
            Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
            configClasses.removeAll(alreadyParsed);
            if (this.reader == null) {
               this.reader = new ConfigurationClassBeanDefinitionReader(
                  registry, this.sourceExtractor, this.resourceLoader, this.environment, this.importBeanNameGenerator, parser.getImportRegistry()
               );
            }

            this.reader.loadBeanDefinitions(configClasses);

            for (ConfigurationClass configClass : configClasses) {
               this.beanRegistrars.addAll(configClass.getBeanRegistrars());
            }

            alreadyParsed.addAll(configClasses);
            processConfig.tag("classCount", () -> String.valueOf(configClasses.size())).end();
            candidates.clear();
            if (registry.getBeanDefinitionCount() > candidateNames.length) {
               String[] newCandidateNames = registry.getBeanDefinitionNames();
               Set<String> oldCandidateNames = Set.of(candidateNames);
               Set<String> alreadyParsedClasses = CollectionUtils.newHashSet(alreadyParsed.size());

               for (ConfigurationClass configurationClass : alreadyParsed) {
                  alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
               }

               for (String candidateName : newCandidateNames) {
                  if (!oldCandidateNames.contains(candidateName)) {
                     BeanDefinition bd = registry.getBeanDefinition(candidateName);
                     if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory)
                        && !alreadyParsedClasses.contains(bd.getBeanClassName())) {
                        candidates.add(new BeanDefinitionHolder(bd, candidateName));
                     }
                  }
               }

               candidateNames = newCandidateNames;
            }
         } while (!candidates.isEmpty());

         if (singletonRegistry != null && !singletonRegistry.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
            singletonRegistry.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
         }

         this.propertySourceDescriptors = parser.getPropertySourceDescriptors();
         if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory cachingMetadataReaderFactory) {
            cachingMetadataReaderFactory.clearCache();
         }
      }
   }

   public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
      StartupStep enhanceConfigClasses = this.applicationStartup.start("spring.context.config-classes.enhance");
      Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();

      for (String beanName : beanFactory.getBeanDefinitionNames()) {
         BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
         Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
         AnnotationMetadata annotationMetadata = null;
         MethodMetadata methodMetadata = null;
         if (beanDef instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
            annotationMetadata = annotatedBeanDefinition.getMetadata();
            methodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
         }

         if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition abd && !abd.hasBeanClass()) {
            boolean liteConfigurationCandidateWithoutBeanMethods = "lite".equals(configClassAttr)
               && annotationMetadata != null
               && !ConfigurationClassUtils.hasBeanMethods(annotationMetadata);
            if (!liteConfigurationCandidateWithoutBeanMethods) {
               try {
                  abd.resolveBeanClass(this.beanClassLoader);
               } catch (Throwable ex) {
                  throw new IllegalStateException("Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
               }
            }
         }

         if ("full".equals(configClassAttr)) {
            if (!(beanDef instanceof AbstractBeanDefinition abd)) {
               throw new BeanDefinitionStoreException(
                  "Cannot enhance @Configuration bean definition '" + beanName + "' since it is not stored in an AbstractBeanDefinition subclass"
               );
            }

            if (beanFactory.containsSingleton(beanName)) {
               if (this.logger.isWarnEnabled()) {
                  this.logger
                     .warn(
                        "Cannot enhance @Configuration bean definition '"
                           + beanName
                           + "' since its singleton instance has been created too early. The typical cause is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor return type: Consider declaring such methods as 'static' and/or marking the containing configuration class as 'proxyBeanMethods=false'."
                     );
               }
            } else {
               configBeanDefs.put(beanName, abd);
            }
         }
      }

      if (configBeanDefs.isEmpty()) {
         enhanceConfigClasses.end();
      } else {
         ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();

         for (Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
            AbstractBeanDefinition beanDef = entry.getValue();
            beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
            Class<?> configClass = beanDef.getBeanClass();
            Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
            if (configClass != enhancedClass) {
               if (this.logger.isTraceEnabled()) {
                  this.logger
                     .trace(
                        String.format(
                           "Replacing bean definition '%s' existing class '%s' with enhanced class '%s'",
                           entry.getKey(),
                           configClass.getName(),
                           enhancedClass.getName()
                        )
                     );
               }

               beanDef.setBeanClass(enhancedClass);
            }
         }

         enhanceConfigClasses.tag("classCount", () -> String.valueOf(configBeanDefs.keySet().size())).end();
      }
   }

   private static class BeanRegistrarAotContribution implements BeanFactoryInitializationAotContribution {
      private static final String CUSTOMIZER_MAP_VARIABLE = "customizers";
      private static final String ENVIRONMENT_VARIABLE = "environment";
      private final MultiValueMap<String, BeanRegistrar> beanRegistrars;
      private final ConfigurableListableBeanFactory beanFactory;
      private final AotServices<BeanRegistrationAotProcessor> aotProcessors;

      public BeanRegistrarAotContribution(MultiValueMap<String, BeanRegistrar> beanRegistrars, ConfigurableListableBeanFactory beanFactory) {
         this.beanRegistrars = beanRegistrars;
         this.beanFactory = beanFactory;
         this.aotProcessors = AotServices.factoriesAndBeans(this.beanFactory).load(BeanRegistrationAotProcessor.class);
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         GeneratedMethod generatedMethod = beanFactoryInitializationCode.getMethods()
            .add(
               "applyBeanRegistrars",
               builder -> this.generateApplyBeanRegistrarsMethod(builder, generationContext, beanFactoryInitializationCode.getClassName())
            );
         beanFactoryInitializationCode.addInitializer(generatedMethod.toMethodReference());
      }

      private void generateApplyBeanRegistrarsMethod(Builder method, GenerationContext generationContext, ClassName className) {
         ReflectionHints reflectionHints = generationContext.getRuntimeHints().reflection();
         method.addJavadoc("Apply bean registrars.", new Object[0]);
         method.addModifiers(new Modifier[]{Modifier.PRIVATE});
         method.addParameter(ListableBeanFactory.class, "beanFactory", new Modifier[0]);
         method.addParameter(Environment.class, "environment", new Modifier[0]);
         method.addCode(this.generateCustomizerMap());

         for (String name : this.beanFactory.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = this.beanFactory.getMergedBeanDefinition(name);
            if (beanDefinition.getSource() instanceof Class<?> sourceClass && BeanRegistrar.class.isAssignableFrom(sourceClass)) {
               for (BeanRegistrationAotProcessor aotProcessor : this.aotProcessors) {
                  BeanRegistrationAotContribution contribution = aotProcessor.processAheadOfTime(RegisteredBean.of(this.beanFactory, name));
                  if (contribution != null) {
                     contribution.applyTo(
                        generationContext,
                        new ConfigurationClassPostProcessor.BeanRegistrarAotContribution.UnsupportedBeanRegistrationCode(name, aotProcessor.getClass())
                     );
                  }
               }

               if (beanDefinition instanceof RootBeanDefinition rootBeanDefinition) {
                  if (rootBeanDefinition.getPreferredConstructors() != null) {
                     for (Constructor<?> constructor : rootBeanDefinition.getPreferredConstructors()) {
                        reflectionHints.registerConstructor(constructor, ExecutableMode.INVOKE);
                     }
                  }

                  if (!ObjectUtils.isEmpty(rootBeanDefinition.getInitMethodNames())) {
                     method.addCode(
                        this.generateInitDestroyMethods(
                           name, rootBeanDefinition, rootBeanDefinition.getInitMethodNames(), "setInitMethodNames", reflectionHints
                        )
                     );
                  }

                  if (!ObjectUtils.isEmpty(rootBeanDefinition.getDestroyMethodNames())) {
                     method.addCode(
                        this.generateInitDestroyMethods(
                           name, rootBeanDefinition, rootBeanDefinition.getDestroyMethodNames(), "setDestroyMethodNames", reflectionHints
                        )
                     );
                  }

                  this.checkUnsupportedFeatures(rootBeanDefinition);
               }
            }
         }

         method.addCode(this.generateRegisterCode(className, generationContext));
      }

      private void checkUnsupportedFeatures(AbstractBeanDefinition beanDefinition) {
         if (!ObjectUtils.isEmpty(beanDefinition.getFactoryBeanName())) {
            throw new UnsupportedOperationException("AOT post processing of the factory bean name is not supported yet with BeanRegistrar");
         }

         if (beanDefinition.hasConstructorArgumentValues()) {
            throw new UnsupportedOperationException("AOT post processing of argument values is not supported yet with BeanRegistrar");
         }

         if (!beanDefinition.getQualifiers().isEmpty()) {
            throw new UnsupportedOperationException("AOT post processing of qualifiers is not supported yet with BeanRegistrar");
         }
      }

      private CodeBlock generateCustomizerMap() {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         code.addStatement(
            "$T<$T, $T> $L = new $T<>()",
            new Object[]{MultiValueMap.class, String.class, BeanDefinitionCustomizer.class, "customizers", LinkedMultiValueMap.class}
         );
         return code.build();
      }

      private CodeBlock generateRegisterCode(ClassName className, GenerationContext generationContext) {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         org.springframework.javapoet.CodeBlock.Builder metadataReaderFactoryCode = null;
         NameAllocator nameAllocator = new NameAllocator();

         for (Entry<String, List<BeanRegistrar>> beanRegistrarEntry : this.beanRegistrars.entrySet()) {
            for (BeanRegistrar beanRegistrar : beanRegistrarEntry.getValue()) {
               String beanRegistrarName = nameAllocator.newName(StringUtils.uncapitalize(beanRegistrar.getClass().getSimpleName()));
               Constructor<?> constructor = BeanUtils.getResolvableConstructor(beanRegistrar.getClass());
               boolean visible = this.isVisible(constructor, className);
               if (visible) {
                  code.addStatement("$T $L = new $T()", new Object[]{beanRegistrar.getClass(), beanRegistrarName, beanRegistrar.getClass()});
               } else {
                  try {
                     Class<?> configClass = ClassUtils.forName(beanRegistrarEntry.getKey(), beanRegistrar.getClass().getClassLoader());
                     GeneratedClass generatedClass = generationContext.getGeneratedClasses()
                        .getOrAddForFeatureComponent(
                           "BeanRegistrars",
                           configClass,
                           type -> type.addJavadoc("Bean registrars for {@link $T}.", new Object[]{configClass}).addModifiers(new Modifier[]{Modifier.PUBLIC})
                        );
                     GeneratedMethod generatedMethod = generatedClass.getMethods()
                        .add(
                           "get" + beanRegistrar.getClass().getSimpleName(),
                           method -> method.addModifiers(new Modifier[]{Modifier.PUBLIC, Modifier.STATIC})
                              .returns(BeanRegistrar.class)
                              .addStatement("return new $T()", new Object[]{beanRegistrar.getClass()})
                        );
                     code.addStatement(
                        "$T $L = $L",
                        new Object[]{
                           BeanRegistrar.class, beanRegistrarName, generatedMethod.toMethodReference().toInvokeCodeBlock(ArgumentCodeGenerator.none())
                        }
                     );
                  } catch (ClassNotFoundException ex) {
                     throw new IllegalStateException(ex);
                  }
               }

               if (beanRegistrar instanceof ImportAware) {
                  if (metadataReaderFactoryCode == null) {
                     metadataReaderFactoryCode = CodeBlock.builder();
                     metadataReaderFactoryCode.addStatement(
                        "$T metadataReaderFactory = new $T()", new Object[]{MetadataReaderFactory.class, CachingMetadataReaderFactory.class}
                     );
                  }

                  CodeBlock setImportMetadataCode;
                  if (visible) {
                     setImportMetadataCode = CodeBlock.builder()
                        .addStatement(
                           "$L.setImportMetadata(metadataReaderFactory.getMetadataReader($S).getAnnotationMetadata())",
                           new Object[]{beanRegistrarName, beanRegistrarEntry.getKey()}
                        )
                        .build();
                  } else {
                     setImportMetadataCode = CodeBlock.builder()
                        .addStatement(
                           "(($T)$L).setImportMetadata(metadataReaderFactory.getMetadataReader($S).getAnnotationMetadata())",
                           new Object[]{ImportAware.class, beanRegistrarName, beanRegistrarEntry.getKey()}
                        )
                        .build();
                  }

                  code.beginControlFlow("try", new Object[0])
                     .add(setImportMetadataCode)
                     .nextControlFlow("catch ($T ex)", new Object[]{IOException.class})
                     .addStatement(
                        "throw new $T(\"Failed to read metadata for '$L'\", ex)", new Object[]{IllegalStateException.class, beanRegistrarEntry.getKey()}
                     )
                     .endControlFlow();
                  generationContext.getRuntimeHints().resources().registerType(TypeReference.of(beanRegistrarEntry.getKey()));
               }

               code.addStatement(
                  "$L.register(new $T(($T)$L, $L, $L, $L.getClass(), $L), $L)",
                  new Object[]{
                     beanRegistrarName,
                     BeanRegistryAdapter.class,
                     BeanDefinitionRegistry.class,
                     "beanFactory",
                     "beanFactory",
                     "environment",
                     beanRegistrarName,
                     "customizers",
                     "environment"
                  }
               );
            }
         }

         return metadataReaderFactoryCode == null ? code.build() : metadataReaderFactoryCode.add(code.build()).build();
      }

      private boolean isVisible(Constructor<?> ctor, ClassName className) {
         AccessControl classAccessControl = AccessControl.forClass(ctor.getDeclaringClass());
         AccessControl memberAccessControl = AccessControl.forMember(ctor);
         Visibility visibility = AccessControl.lowest(new AccessControl[]{classAccessControl, memberAccessControl}).getVisibility();
         return visibility == Visibility.PUBLIC
            || visibility != Visibility.PRIVATE && ctor.getDeclaringClass().getPackageName().equals(className.packageName());
      }

      private CodeBlock generateInitDestroyMethods(
         String beanName, AbstractBeanDefinition beanDefinition, String[] methodNames, String method, ReflectionHints reflectionHints
      ) {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         reflectionHints.registerType(TypeReference.of("org.reactivestreams.Publisher"), new MemberCategory[0]);
         Class<?> beanType = ClassUtils.getUserClass(beanDefinition.getResolvableType().toClass());
         Arrays.stream(methodNames).forEach(methodName -> addInitDestroyHint(beanType, methodName, reflectionHints));
         CodeBlock arguments = Arrays.stream(methodNames).map(name -> CodeBlock.of("$S", new Object[]{name})).collect(CodeBlock.joining(", "));
         code.addStatement(
            "$L.add($S, $L -> (($T)$L).$L($L))", new Object[]{"customizers", beanName, "bd", AbstractBeanDefinition.class, "bd", method, arguments}
         );
         return code.build();
      }

      private static void addInitDestroyHint(Class<?> beanUserClass, String methodName, ReflectionHints reflectionHints) {
         Class<?> methodDeclaringClass = beanUserClass;
         int indexOfDot = methodName.lastIndexOf(46);
         if (indexOfDot > 0) {
            String className = methodName.substring(0, indexOfDot);
            methodName = methodName.substring(indexOfDot + 1);
            if (!beanUserClass.getName().equals(className)) {
               try {
                  methodDeclaringClass = ClassUtils.forName(className, beanUserClass.getClassLoader());
               } catch (Throwable ex) {
                  throw new IllegalStateException("Failed to load Class [" + className + "] from ClassLoader [" + beanUserClass.getClassLoader() + "]", ex);
               }
            }
         }

         Method method = ReflectionUtils.findMethod(methodDeclaringClass, methodName);
         if (method != null) {
            reflectionHints.registerMethod(method, ExecutableMode.INVOKE);
            Method publiclyAccessibleMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(method, beanUserClass);
            if (!publiclyAccessibleMethod.equals(method)) {
               reflectionHints.registerMethod(publiclyAccessibleMethod, ExecutableMode.INVOKE);
            }
         }
      }

      static class UnsupportedBeanRegistrationCode implements BeanRegistrationCode {
         private final String message;

         public UnsupportedBeanRegistrationCode(String beanName, Class<?> aotProcessorClass) {
            this.message = "Code generation attempted for bean "
               + beanName
               + " by the AOT Processor "
               + aotProcessorClass
               + " is not supported with BeanRegistrar yet";
         }

         public ClassName getClassName() {
            throw new UnsupportedOperationException(this.message);
         }

         public GeneratedMethods getMethods() {
            throw new UnsupportedOperationException(this.message);
         }

         public void addInstancePostProcessor(MethodReference methodReference) {
            throw new UnsupportedOperationException(this.message);
         }
      }
   }

   private static class ConfigurationClassProxyBeanRegistrationCodeFragments extends BeanRegistrationCodeFragmentsDecorator {
      private final RegisteredBean registeredBean;
      private final Class<?> proxyClass;

      public ConfigurationClassProxyBeanRegistrationCodeFragments(BeanRegistrationCodeFragments codeFragments, RegisteredBean registeredBean) {
         super(codeFragments);
         this.registeredBean = registeredBean;
         this.proxyClass = registeredBean.getBeanType().toClass();
      }

      public CodeBlock generateSetBeanDefinitionPropertiesCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition, Predicate<String> attributeFilter
      ) {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         code.add(super.generateSetBeanDefinitionPropertiesCode(generationContext, beanRegistrationCode, beanDefinition, attributeFilter));
         code.addStatement("$T.initializeConfigurationClass($T.class)", new Object[]{ConfigurationClassUtils.class, ClassUtils.getUserClass(this.proxyClass)});
         return code.build();
      }

      public CodeBlock generateInstanceSupplierCode(
         GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode, boolean allowDirectSupplierShortcut
      ) {
         InstantiationDescriptor instantiationDescriptor = this.proxyInstantiationDescriptor(
            generationContext.getRuntimeHints(), this.registeredBean.resolveInstantiationDescriptor()
         );
         return new InstanceSupplierCodeGenerator(
               generationContext, beanRegistrationCode.getClassName(), beanRegistrationCode.getMethods(), allowDirectSupplierShortcut
            )
            .generateCode(this.registeredBean, instantiationDescriptor);
      }

      private InstantiationDescriptor proxyInstantiationDescriptor(RuntimeHints runtimeHints, InstantiationDescriptor instantiationDescriptor) {
         Executable userExecutable = instantiationDescriptor.executable();
         if (userExecutable instanceof Constructor<?> userConstructor) {
            try {
               runtimeHints.reflection().registerType(userConstructor.getDeclaringClass(), new MemberCategory[0]);
               Constructor<?> constructor = this.proxyClass.getConstructor(userExecutable.getParameterTypes());
               return new InstantiationDescriptor(constructor);
            } catch (NoSuchMethodException ex) {
               throw new IllegalStateException("No matching constructor found on proxy " + this.proxyClass, ex);
            }
         } else {
            return instantiationDescriptor;
         }
      }
   }

   private static class ImportAwareAotContribution implements BeanFactoryInitializationAotContribution {
      private static final String BEAN_FACTORY_VARIABLE = "beanFactory";
      private static final ParameterizedTypeName STRING_STRING_MAP = ParameterizedTypeName.get(Map.class, new Type[]{String.class, String.class});
      private static final String MAPPINGS_VARIABLE = "mappings";
      private static final String BEAN_DEFINITION_VARIABLE = "beanDefinition";
      private static final String BEAN_NAME = "org.springframework.context.annotation.internalImportAwareAotProcessor";
      private final ConfigurableListableBeanFactory beanFactory;

      public ImportAwareAotContribution(ConfigurableListableBeanFactory beanFactory) {
         this.beanFactory = beanFactory;
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         Map<String, String> mappings = this.buildImportAwareMappings();
         if (!mappings.isEmpty()) {
            GeneratedMethod generatedMethod = beanFactoryInitializationCode.getMethods()
               .add("addImportAwareBeanPostProcessors", method -> this.generateAddPostProcessorMethod(method, mappings));
            beanFactoryInitializationCode.addInitializer(generatedMethod.toMethodReference());
            ResourceHints hints = generationContext.getRuntimeHints().resources();
            mappings.forEach((target, from) -> hints.registerType(TypeReference.of(from)));
         }
      }

      private void generateAddPostProcessorMethod(Builder method, Map<String, String> mappings) {
         method.addJavadoc("Add ImportAwareBeanPostProcessor to support ImportAware beans.", new Object[0]);
         method.addModifiers(new Modifier[]{Modifier.PRIVATE});
         method.addParameter(DefaultListableBeanFactory.class, "beanFactory", new Modifier[0]);
         method.addCode(this.generateAddPostProcessorCode(mappings));
      }

      private CodeBlock generateAddPostProcessorCode(Map<String, String> mappings) {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         code.addStatement("$T $L = new $T<>()", new Object[]{STRING_STRING_MAP, "mappings", HashMap.class});
         mappings.forEach((type, from) -> code.addStatement("$L.put($S, $S)", new Object[]{"mappings", type, from}));
         code.addStatement(
            "$T $L = new $T($T.class)",
            new Object[]{RootBeanDefinition.class, "beanDefinition", RootBeanDefinition.class, ImportAwareAotBeanPostProcessor.class}
         );
         code.addStatement("$L.setRole($T.ROLE_INFRASTRUCTURE)", new Object[]{"beanDefinition", BeanDefinition.class});
         code.addStatement("$L.setInstanceSupplier(() -> new $T($L))", new Object[]{"beanDefinition", ImportAwareAotBeanPostProcessor.class, "mappings"});
         code.addStatement(
            "$L.registerBeanDefinition($S, $L)",
            new Object[]{"beanFactory", "org.springframework.context.annotation.internalImportAwareAotProcessor", "beanDefinition"}
         );
         return code.build();
      }

      private Map<String, String> buildImportAwareMappings() {
         ImportRegistry importRegistry = (ImportRegistry)this.beanFactory
            .getBean(ConfigurationClassPostProcessor.IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
         Map<String, String> mappings = new LinkedHashMap<>();

         for (String name : this.beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = this.beanFactory.getType(name);
            if (beanType != null && ImportAware.class.isAssignableFrom(beanType)) {
               String target = ClassUtils.getUserClass(beanType).getName();
               AnnotationMetadata from = importRegistry.getImportingClassFor(target);
               if (from != null) {
                  mappings.put(target, from.getClassName());
               }
            }
         }

         return mappings;
      }
   }

   private static class ImportAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {
      private final BeanFactory beanFactory;

      public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
         this.beanFactory = beanFactory;
      }

      public @Nullable PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
         if (bean instanceof ConfigurationClassEnhancer.EnhancedConfiguration enhancedConfiguration) {
            enhancedConfiguration.setBeanFactory(this.beanFactory);
         }

         return pvs;
      }

      public Object postProcessBeforeInitialization(Object bean, String beanName) {
         if (bean instanceof ImportAware importAware) {
            ImportRegistry ir = (ImportRegistry)this.beanFactory.getBean(ConfigurationClassPostProcessor.IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
            AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean).getName());
            if (importingClass != null) {
               importAware.setImportMetadata(importingClass);
            }
         }

         return bean;
      }
   }

   private static class PropertySourcesAotContribution implements BeanFactoryInitializationAotContribution {
      private static final String ENVIRONMENT_VARIABLE = "environment";
      private static final String RESOURCE_LOADER_VARIABLE = "resourceLoader";
      private final Log logger = LogFactory.getLog(this.getClass());
      private final List<PropertySourceDescriptor> descriptors;
      private final Function<String, @Nullable Resource> resourceResolver;

      PropertySourcesAotContribution(List<PropertySourceDescriptor> descriptors, Function<String, @Nullable Resource> resourceResolver) {
         this.descriptors = descriptors;
         this.resourceResolver = resourceResolver;
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         this.registerRuntimeHints(generationContext.getRuntimeHints());
         GeneratedMethod generatedMethod = beanFactoryInitializationCode.getMethods()
            .add("processPropertySources", this::generateAddPropertySourceProcessorMethod);
         beanFactoryInitializationCode.addInitializer(generatedMethod.toMethodReference());
      }

      private void registerRuntimeHints(RuntimeHints hints) {
         for (PropertySourceDescriptor descriptor : this.descriptors) {
            Class<?> factoryClass = descriptor.propertySourceFactory();
            if (factoryClass != null) {
               hints.reflection().registerType(factoryClass, new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS});
            }

            for (String location : descriptor.locations()) {
               if (location.startsWith("classpath*:") || location.startsWith("classpath:") && (location.contains("*") || location.contains("?"))) {
                  if (this.logger.isWarnEnabled()) {
                     this.logger
                        .warn(
                           "Runtime hint registration is not supported for the 'classpath*:' prefix or wildcards in @PropertySource locations. Please manually register a resource hint for each property source location represented by '%s'."
                              .formatted(location)
                        );
                  }
               } else {
                  Resource resource = this.resourceResolver.apply(location);
                  if (resource instanceof ClassPathResource classPathResource && classPathResource.exists()) {
                     hints.resources().registerPattern(classPathResource.getPath());
                  }
               }
            }
         }
      }

      private void generateAddPropertySourceProcessorMethod(Builder method) {
         method.addJavadoc("Apply known @PropertySources to the environment.", new Object[0]);
         method.addModifiers(new Modifier[]{Modifier.PRIVATE});
         method.addParameter(ConfigurableEnvironment.class, "environment", new Modifier[0]);
         method.addParameter(ResourceLoader.class, "resourceLoader", new Modifier[0]);
         method.addCode(this.generateAddPropertySourceProcessorCode());
      }

      private CodeBlock generateAddPropertySourceProcessorCode() {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         String processorVariable = "processor";
         code.addStatement(
            "$T $L = new $T($L, $L)",
            new Object[]{PropertySourceProcessor.class, processorVariable, PropertySourceProcessor.class, "environment", "resourceLoader"}
         );
         code.beginControlFlow("try", new Object[0]);

         for (PropertySourceDescriptor descriptor : this.descriptors) {
            code.addStatement("$L.processPropertySource($L)", new Object[]{processorVariable, this.generatePropertySourceDescriptorCode(descriptor)});
         }

         code.nextControlFlow("catch ($T ex)", new Object[]{IOException.class});
         code.addStatement("throw new $T(ex)", new Object[]{UncheckedIOException.class});
         code.endControlFlow();
         return code.build();
      }

      private CodeBlock generatePropertySourceDescriptorCode(PropertySourceDescriptor descriptor) {
         org.springframework.javapoet.CodeBlock.Builder code = CodeBlock.builder();
         code.add("new $T(", new Object[]{PropertySourceDescriptor.class});
         CodeBlock values = descriptor.locations().stream().map(value -> CodeBlock.of("$S", new Object[]{value})).collect(CodeBlock.joining(", "));
         if (descriptor.name() == null && descriptor.propertySourceFactory() == null && descriptor.encoding() == null && !descriptor.ignoreResourceNotFound()) {
            code.add("$L)", new Object[]{values});
         } else {
            List<CodeBlock> arguments = new ArrayList<>();
            arguments.add(CodeBlock.of("$T.of($L)", new Object[]{List.class, values}));
            arguments.add(CodeBlock.of("$L", new Object[]{descriptor.ignoreResourceNotFound()}));
            arguments.add(this.handleNull(descriptor.name(), () -> CodeBlock.of("$S", new Object[]{descriptor.name()})));
            arguments.add(this.handleNull(descriptor.propertySourceFactory(), () -> CodeBlock.of("$T.class", new Object[]{descriptor.propertySourceFactory()})));
            arguments.add(this.handleNull(descriptor.encoding(), () -> CodeBlock.of("$S", new Object[]{descriptor.encoding()})));
            code.add(CodeBlock.join(arguments, ", "));
            code.add(")", new Object[0]);
         }

         return code.build();
      }

      private CodeBlock handleNull(@Nullable Object value, Supplier<CodeBlock> nonNull) {
         return value == null ? CodeBlock.of("null", new Object[0]) : nonNull.get();
      }
   }
}
