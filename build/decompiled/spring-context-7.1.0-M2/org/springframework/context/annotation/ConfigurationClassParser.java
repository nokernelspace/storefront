package org.springframework.context.annotation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertySourceDescriptor;
import org.springframework.core.io.support.PropertySourceProcessor;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

class ConfigurationClassParser {
   private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className -> className.startsWith("java.lang.annotation.")
      || className.startsWith("org.springframework.stereotype.");
   private static final Predicate<Condition> REGISTER_BEAN_CONDITION_FILTER = condition -> condition instanceof ConfigurationCondition configurationCondition
      && ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN.equals(configurationCondition.getConfigurationPhase());
   private static final Comparator<ConfigurationClassParser.DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR = (o1, o2) -> AnnotationAwareOrderComparator.INSTANCE
      .compare(o1.getImportSelector(), o2.getImportSelector());
   private final Log logger = LogFactory.getLog(this.getClass());
   private final MetadataReaderFactory metadataReaderFactory;
   private final ProblemReporter problemReporter;
   private final Environment environment;
   private final ResourceLoader resourceLoader;
   private final @Nullable PropertySourceRegistry propertySourceRegistry;
   private final BeanDefinitionRegistry registry;
   private final ComponentScanAnnotationParser componentScanParser;
   private final ConditionEvaluator conditionEvaluator;
   private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();
   private final MultiValueMap<String, ConfigurationClass> knownSuperclasses = new LinkedMultiValueMap();
   private final ConfigurationClassParser.ImportStack importStack = new ConfigurationClassParser.ImportStack();
   private final ConfigurationClassParser.DeferredImportSelectorHandler deferredImportSelectorHandler = new ConfigurationClassParser.DeferredImportSelectorHandler(
      
   );
   private final ConfigurationClassParser.SourceClass objectSourceClass = new ConfigurationClassParser.SourceClass(Object.class);

   public ConfigurationClassParser(
      MetadataReaderFactory metadataReaderFactory,
      ProblemReporter problemReporter,
      Environment environment,
      ResourceLoader resourceLoader,
      BeanNameGenerator componentScanBeanNameGenerator,
      BeanDefinitionRegistry registry
   ) {
      this.metadataReaderFactory = metadataReaderFactory;
      this.problemReporter = problemReporter;
      this.environment = environment;
      this.resourceLoader = resourceLoader;
      this.propertySourceRegistry = this.environment instanceof ConfigurableEnvironment ce
         ? new PropertySourceRegistry(new PropertySourceProcessor(ce, this.resourceLoader))
         : null;
      this.registry = registry;
      this.componentScanParser = new ComponentScanAnnotationParser(environment, resourceLoader, componentScanBeanNameGenerator, registry);
      this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
   }

   public void parse(Set<BeanDefinitionHolder> configCandidates) {
      for (BeanDefinitionHolder holder : configCandidates) {
         BeanDefinition bd = holder.getBeanDefinition();

         try {
            ConfigurationClass configClass;
            if (bd instanceof AnnotatedBeanDefinition annotatedBeanDef) {
               configClass = this.parse(annotatedBeanDef, holder.getBeanName());
            } else if (bd instanceof AbstractBeanDefinition abstractBeanDef && abstractBeanDef.hasBeanClass()) {
               configClass = this.parse(abstractBeanDef.getBeanClass(), holder.getBeanName());
            } else {
               configClass = this.parse(bd.getBeanClassName(), holder.getBeanName());
            }

            if (!configClass.getMetadata().isAbstract()
               && !configClass.hasNonStaticBeanMethods()
               && "full".equals(bd.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE))) {
               bd.setAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE, "lite");
            }
         } catch (BeanDefinitionStoreException ex) {
            throw ex;
         } catch (Throwable ex) {
            throw new BeanDefinitionStoreException("Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
         }
      }

      this.deferredImportSelectorHandler.process();
   }

   private ConfigurationClass parse(AnnotatedBeanDefinition beanDef, String beanName) {
      ConfigurationClass configClass = new ConfigurationClass(beanDef.getMetadata(), beanName, beanDef instanceof ScannedGenericBeanDefinition);
      this.processConfigurationClass(configClass, DEFAULT_EXCLUSION_FILTER);
      return configClass;
   }

   private ConfigurationClass parse(Class<?> clazz, String beanName) {
      ConfigurationClass configClass = new ConfigurationClass(clazz, beanName);
      this.processConfigurationClass(configClass, DEFAULT_EXCLUSION_FILTER);
      return configClass;
   }

   final ConfigurationClass parse(@Nullable String className, String beanName) throws IOException {
      Assert.notNull(className, "No bean class name for configuration class bean definition");
      MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
      ConfigurationClass configClass = new ConfigurationClass(reader, beanName);
      this.processConfigurationClass(configClass, DEFAULT_EXCLUSION_FILTER);
      return configClass;
   }

   void validate() {
      for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
         configClass.validate(this.problemReporter);
      }
   }

   Set<ConfigurationClass> getConfigurationClasses() {
      return this.configurationClasses.keySet();
   }

   List<PropertySourceDescriptor> getPropertySourceDescriptors() {
      return this.propertySourceRegistry != null ? this.propertySourceRegistry.getDescriptors() : Collections.emptyList();
   }

   ImportRegistry getImportRegistry() {
      return this.importStack;
   }

   protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) {
      if (!this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION)) {
         ConfigurationClass existingClass = this.configurationClasses.get(configClass);
         if (existingClass != null) {
            if (configClass.isImported()) {
               if (existingClass.isImported()) {
                  existingClass.mergeImportedBy(configClass);
               }

               return;
            }

            if (configClass.isScanned()) {
               if (existingClass.isImported()) {
                  String beanName = configClass.getBeanName();
                  if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
                     this.registry.removeBeanDefinition(beanName);
                  }
               }

               return;
            }

            this.configurationClasses.remove(configClass);
            this.removeKnownSuperclass(configClass.getMetadata().getClassName(), false);
         }

         ConfigurationClassParser.SourceClass sourceClass = null;

         try {
            sourceClass = this.asSourceClass(configClass, filter);

            do {
               sourceClass = this.doProcessConfigurationClass(configClass, sourceClass, filter);
            } while (sourceClass != null);
         } catch (IOException ex) {
            throw new BeanDefinitionStoreException("I/O failure while processing configuration class [" + sourceClass + "]", ex);
         }

         this.configurationClasses.put(configClass, configClass);
      }
   }

   protected final ConfigurationClassParser.@Nullable SourceClass doProcessConfigurationClass(
      ConfigurationClass configClass, ConfigurationClassParser.SourceClass sourceClass, Predicate<String> filter
   ) throws IOException {
      if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
         this.processMemberClasses(configClass, sourceClass, filter);
      }

      for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
         sourceClass.getMetadata(), PropertySource.class, PropertySources.class, true
      )) {
         if (this.propertySourceRegistry != null) {
            this.propertySourceRegistry.processPropertySource(propertySource);
         } else {
            this.logger
               .info(
                  "Ignoring @PropertySource annotation on ["
                     + sourceClass.getMetadata().getClassName()
                     + "]. Reason: Environment must implement ConfigurableEnvironment"
               );
         }
      }

      Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
         sourceClass.getMetadata(), ComponentScan.class, ComponentScans.class, MergedAnnotation::isDirectlyPresent
      );
      if (componentScans.isEmpty()) {
         componentScans = AnnotationConfigUtils.attributesForRepeatable(
            sourceClass.getMetadata(), ComponentScan.class, ComponentScans.class, MergedAnnotation::isMetaPresent
         );
      }

      if (!componentScans.isEmpty()) {
         List<Condition> registerBeanConditions = this.collectRegisterBeanConditions(configClass);
         if (!registerBeanConditions.isEmpty()) {
            throw new ApplicationContextException(
               "Component scan for configuration class [%s] could not be used with conditions in REGISTER_BEAN phase: %s"
                  .formatted(configClass.getMetadata().getClassName(), registerBeanConditions)
            );
         }

         for (AnnotationAttributes componentScan : componentScans) {
            for (BeanDefinitionHolder holder : this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName())) {
               BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
               if (bdCand == null) {
                  bdCand = holder.getBeanDefinition();
               }

               if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
                  this.parse(bdCand.getBeanClassName(), holder.getBeanName());
               }
            }
         }
      }

      this.processImports(configClass, sourceClass, this.getImports(sourceClass), filter, true);
      AnnotationAttributes importResource = AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
      if (importResource != null) {
         String[] resources = importResource.getStringArray("locations");
         Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");

         for (String resource : resources) {
            String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
            configClass.addImportedResource(resolvedResource, readerClass);
         }
      }

      for (MethodMetadata methodMetadata : this.retrieveBeanMethodMetadata(sourceClass)) {
         if (!methodMetadata.isAnnotated("kotlin.jvm.JvmStatic") || methodMetadata.isStatic()) {
            configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
         }
      }

      this.processInterfaces(configClass, sourceClass);
      if (sourceClass.getMetadata().hasSuperClass()) {
         String superclass = sourceClass.getMetadata().getSuperClassName();
         if (superclass != null && !superclass.startsWith("java")) {
            boolean superclassKnown = this.knownSuperclasses.containsKey(superclass);
            this.knownSuperclasses.add(superclass, configClass);
            if (!superclassKnown) {
               return sourceClass.getSuperClass();
            }
         }
      }

      return null;
   }

   private void processMemberClasses(ConfigurationClass configClass, ConfigurationClassParser.SourceClass sourceClass, Predicate<String> filter) throws IOException {
      Collection<ConfigurationClassParser.SourceClass> memberClasses = sourceClass.getMemberClasses();
      if (!memberClasses.isEmpty()) {
         List<ConfigurationClassParser.SourceClass> candidates = new ArrayList<>(memberClasses.size());

         for (ConfigurationClassParser.SourceClass memberClass : memberClasses) {
            if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata())
               && !memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
               candidates.add(memberClass);
            }
         }

         OrderComparator.sort(candidates);

         for (ConfigurationClassParser.SourceClass candidate : candidates) {
            if (this.importStack.contains(configClass)) {
               this.problemReporter.error(new ConfigurationClassParser.CircularImportProblem(configClass, this.importStack));
            } else {
               this.importStack.push(configClass);

               try {
                  this.processConfigurationClass(candidate.asConfigClass(configClass), filter);
               } finally {
                  this.importStack.pop();
               }
            }
         }
      }
   }

   private void processInterfaces(ConfigurationClass configClass, ConfigurationClassParser.SourceClass sourceClass) throws IOException {
      for (ConfigurationClassParser.SourceClass ifc : sourceClass.getInterfaces()) {
         for (MethodMetadata methodMetadata : this.retrieveBeanMethodMetadata(ifc)) {
            if (!methodMetadata.isAbstract()) {
               configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
            }
         }

         this.processInterfaces(configClass, ifc);
      }
   }

   private Set<MethodMetadata> retrieveBeanMethodMetadata(ConfigurationClassParser.SourceClass sourceClass) {
      AnnotationMetadata original = sourceClass.getMetadata();
      Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
      if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
         try {
            AnnotationMetadata asm = this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
            Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
            if (asmMethods.size() >= beanMethods.size()) {
               Set<MethodMetadata> candidateMethods = new LinkedHashSet<>(beanMethods);
               Set<MethodMetadata> selectedMethods = CollectionUtils.newLinkedHashSet(asmMethods.size());

               for (MethodMetadata asmMethod : asmMethods) {
                  Iterator<MethodMetadata> it = candidateMethods.iterator();

                  while (it.hasNext()) {
                     MethodMetadata beanMethod = it.next();
                     if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
                        selectedMethods.add(beanMethod);
                        it.remove();
                        break;
                     }
                  }
               }

               if (selectedMethods.size() == beanMethods.size()) {
                  beanMethods = selectedMethods;
               }
            }
         } catch (IOException ex) {
            this.logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
         }
      }

      return beanMethods;
   }

   private void removeKnownSuperclass(String removedClass, boolean replace) {
      String replacedSuperclass = null;
      ConfigurationClass replacingClass = null;
      Iterator<Entry<String, List<ConfigurationClass>>> it = this.knownSuperclasses.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, List<ConfigurationClass>> entry = it.next();
         if (entry.getValue().removeIf(configClass -> configClass.getMetadata().getClassName().equals(removedClass))) {
            if (entry.getValue().isEmpty()) {
               it.remove();
            } else if (replace && replacingClass == null) {
               replacedSuperclass = entry.getKey();
               replacingClass = entry.getValue().get(0);
            }
         }
      }

      if (replacingClass != null) {
         try {
            ConfigurationClassParser.SourceClass sourceClass = this.asSourceClass(replacingClass, DEFAULT_EXCLUSION_FILTER).getSuperClass();

            while (!sourceClass.getMetadata().getClassName().equals(replacedSuperclass) && sourceClass.getMetadata().getSuperClassName() != null) {
               sourceClass = sourceClass.getSuperClass();
            }

            do {
               sourceClass = this.doProcessConfigurationClass(replacingClass, sourceClass, DEFAULT_EXCLUSION_FILTER);
            } while (sourceClass != null);
         } catch (IOException ex) {
            throw new BeanDefinitionStoreException("I/O failure while removing configuration class [" + removedClass + "]", ex);
         }
      }
   }

   private Set<ConfigurationClassParser.SourceClass> getImports(ConfigurationClassParser.SourceClass sourceClass) throws IOException {
      Set<ConfigurationClassParser.SourceClass> imports = new LinkedHashSet<>();
      this.collectImports(sourceClass, imports, new HashSet<>());
      return imports;
   }

   private void collectImports(
      ConfigurationClassParser.SourceClass sourceClass, Set<ConfigurationClassParser.SourceClass> imports, Set<ConfigurationClassParser.SourceClass> visited
   ) throws IOException {
      if (visited.add(sourceClass)) {
         for (ConfigurationClassParser.SourceClass ifc : sourceClass.getInterfaces()) {
            this.collectImports(ifc, imports, visited);
         }

         for (ConfigurationClassParser.SourceClass annotation : sourceClass.getAnnotations()) {
            String annName = annotation.getMetadata().getClassName();
            if (!annName.equals(Import.class.getName())) {
               this.collectImports(annotation, imports, visited);
            }
         }

         imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
      }
   }

   private void processImports(
      ConfigurationClass configClass,
      ConfigurationClassParser.SourceClass currentSourceClass,
      Collection<ConfigurationClassParser.SourceClass> importCandidates,
      Predicate<String> filter,
      boolean checkForCircularImports
   ) {
      if (!importCandidates.isEmpty()) {
         if (checkForCircularImports && this.isChainedImportOnStack(configClass)) {
            this.problemReporter.error(new ConfigurationClassParser.CircularImportProblem(configClass, this.importStack));
         } else {
            this.importStack.push(configClass);

            try {
               for (ConfigurationClassParser.SourceClass candidate : importCandidates) {
                  if (candidate.isAssignable(ImportSelector.class)) {
                     Class<?> candidateClass = candidate.loadClass();
                     ImportSelector selector = ParserStrategyUtils.instantiateClass(
                        candidateClass, ImportSelector.class, this.environment, this.resourceLoader, this.registry
                     );
                     Predicate<String> selectorFilter = selector.getExclusionFilter();
                     if (selectorFilter != null) {
                        filter = filter.or(selectorFilter);
                     }

                     if (selector instanceof DeferredImportSelector deferredImportSelector) {
                        this.deferredImportSelectorHandler.handle(configClass, deferredImportSelector);
                     } else {
                        String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
                        Collection<ConfigurationClassParser.SourceClass> importSourceClasses = this.asSourceClasses(importClassNames, filter);
                        this.processImports(configClass, currentSourceClass, importSourceClasses, filter, false);
                     }
                  } else if (candidate.isAssignable(BeanRegistrar.class)) {
                     Class<?> candidateClass = candidate.loadClass();
                     BeanRegistrar registrar = (BeanRegistrar)BeanUtils.instantiateClass(candidateClass);
                     AnnotationMetadata metadata = currentSourceClass.getMetadata();
                     if (registrar instanceof ImportAware importAware) {
                        importAware.setImportMetadata(metadata);
                     }

                     configClass.addBeanRegistrar(metadata.getClassName(), registrar);
                  } else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                     Class<?> candidateClass = candidate.loadClass();
                     ImportBeanDefinitionRegistrar registrar = ParserStrategyUtils.instantiateClass(
                        candidateClass, ImportBeanDefinitionRegistrar.class, this.environment, this.resourceLoader, this.registry
                     );
                     configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
                  } else {
                     this.importStack.registerImport(currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
                     this.processConfigurationClass(candidate.asConfigClass(configClass), filter);
                  }
               }
            } catch (BeanDefinitionStoreException ex) {
               throw ex;
            } catch (Throwable ex) {
               throw new BeanDefinitionStoreException(
                  "Failed to process import candidates for configuration class [" + configClass.getMetadata().getClassName() + "]: " + ex.getMessage(), ex
               );
            } finally {
               this.importStack.pop();
            }
         }
      }
   }

   private boolean isChainedImportOnStack(ConfigurationClass configClass) {
      if (this.importStack.contains(configClass)) {
         String configClassName = configClass.getMetadata().getClassName();

         for (AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
            importingClass != null;
            importingClass = this.importStack.getImportingClassFor(importingClass.getClassName())
         ) {
            if (configClassName.equals(importingClass.getClassName())) {
               return true;
            }
         }
      }

      return false;
   }

   private ConfigurationClassParser.SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
      AnnotationMetadata metadata = configurationClass.getMetadata();
      return metadata instanceof StandardAnnotationMetadata standardAnnotationMetadata
         ? this.asSourceClass(standardAnnotationMetadata.getIntrospectedClass(), filter)
         : this.asSourceClass(metadata.getClassName(), filter);
   }

   ConfigurationClassParser.SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
      if (classType != null && !filter.test(classType.getName())) {
         try {
            for (Annotation ann : classType.getDeclaredAnnotations()) {
               AnnotationUtils.validateAnnotation(ann);
            }

            return new ConfigurationClassParser.SourceClass(classType);
         } catch (Throwable ex) {
            return this.asSourceClass(classType.getName(), filter);
         }
      } else {
         return this.objectSourceClass;
      }
   }

   private Collection<ConfigurationClassParser.SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
      List<ConfigurationClassParser.SourceClass> annotatedClasses = new ArrayList<>(classNames.length);

      for (String className : classNames) {
         ConfigurationClassParser.SourceClass sourceClass = this.asSourceClass(className, filter);
         if (this.objectSourceClass != sourceClass) {
            annotatedClasses.add(sourceClass);
         }
      }

      return annotatedClasses;
   }

   ConfigurationClassParser.SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
      if (className == null || filter.test(className)) {
         return this.objectSourceClass;
      }

      if (className.startsWith("java")) {
         try {
            return new ConfigurationClassParser.SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
         } catch (ClassNotFoundException ex) {
            throw new IOException("Failed to load class [" + className + "]", ex);
         }
      } else {
         return new ConfigurationClassParser.SourceClass(this.metadataReaderFactory.getMetadataReader(className));
      }
   }

   private List<Condition> collectRegisterBeanConditions(ConfigurationClass configurationClass) {
      AnnotationMetadata metadata = configurationClass.getMetadata();
      List<Condition> allConditions = new ArrayList<>(this.conditionEvaluator.collectConditions(metadata));
      ConfigurationClass enclosingConfigurationClass = this.getEnclosingConfigurationClass(configurationClass);
      if (enclosingConfigurationClass != null) {
         allConditions.addAll(this.conditionEvaluator.collectConditions(enclosingConfigurationClass.getMetadata()));
      }

      return allConditions.stream().filter(REGISTER_BEAN_CONDITION_FILTER).toList();
   }

   private @Nullable ConfigurationClass getEnclosingConfigurationClass(ConfigurationClass configurationClass) {
      String enclosingClassName = configurationClass.getMetadata().getEnclosingClassName();
      return enclosingClassName != null
         ? configurationClass.getImportedBy()
            .stream()
            .filter(candidate -> enclosingClassName.equals(candidate.getMetadata().getClassName()))
            .findFirst()
            .orElse(null)
         : null;
   }

   private static class CircularImportProblem extends Problem {
      public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
         super(
            String.format(
               "A circular @Import has been detected: Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is already present in the current import stack %s",
               importStack.element().getSimpleName(),
               attemptedImport.getSimpleName(),
               attemptedImport.getSimpleName(),
               importStack
            ),
            new Location(importStack.element().getResource(), attemptedImport.getMetadata())
         );
      }
   }

   private static class DefaultDeferredImportSelectorGroup implements DeferredImportSelector.Group {
      private final List<DeferredImportSelector.Group.Entry> imports = new ArrayList<>();

      @Override
      public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
         for (String importClassName : selector.selectImports(metadata)) {
            this.imports.add(new DeferredImportSelector.Group.Entry(metadata, importClassName));
         }
      }

      @Override
      public Iterable<DeferredImportSelector.Group.Entry> selectImports() {
         return this.imports;
      }
   }

   private static class DeferredImportSelectorGrouping {
      private final DeferredImportSelector.Group group;
      private final List<ConfigurationClassParser.DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

      DeferredImportSelectorGrouping(DeferredImportSelector.Group group) {
         this.group = group;
      }

      void add(ConfigurationClassParser.DeferredImportSelectorHolder deferredImport) {
         this.deferredImports.add(deferredImport);
      }

      Iterable<DeferredImportSelector.Group.Entry> getImports() {
         for (ConfigurationClassParser.DeferredImportSelectorHolder deferredImport : this.deferredImports) {
            this.group.process(deferredImport.getConfigurationClass().getMetadata(), deferredImport.getImportSelector());
         }

         return this.group.selectImports();
      }

      Predicate<String> getCandidateFilter() {
         Predicate<String> mergedFilter = ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER;

         for (ConfigurationClassParser.DeferredImportSelectorHolder deferredImport : this.deferredImports) {
            Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
            if (selectorFilter != null) {
               mergedFilter = mergedFilter.or(selectorFilter);
            }
         }

         return mergedFilter;
      }
   }

   private class DeferredImportSelectorGroupingHandler {
      private final Map<Object, ConfigurationClassParser.DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();
      private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

      void register(ConfigurationClassParser.DeferredImportSelectorHolder deferredImport) {
         Class<? extends DeferredImportSelector.Group> group = deferredImport.getImportSelector().getImportGroup();
         ConfigurationClassParser.DeferredImportSelectorGrouping grouping = this.groupings
            .computeIfAbsent(
               group != null ? group : deferredImport, key -> new ConfigurationClassParser.DeferredImportSelectorGrouping(this.createGroup(group))
            );
         grouping.add(deferredImport);
         this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(), deferredImport.getConfigurationClass());
      }

      void processGroupImports() {
         for (ConfigurationClassParser.DeferredImportSelectorGrouping grouping : this.groupings.values()) {
            Predicate<String> filter = grouping.getCandidateFilter();
            grouping.getImports()
               .forEach(
                  entry -> {
                     ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
                     Assert.state(configurationClass != null, "ConfigurationClass must not be null");

                     try {
                        ConfigurationClassParser.this.processImports(
                           configurationClass,
                           ConfigurationClassParser.this.asSourceClass(configurationClass, filter),
                           Collections.singleton(ConfigurationClassParser.this.asSourceClass(entry.getImportClassName(), filter)),
                           filter,
                           false
                        );
                     } catch (BeanDefinitionStoreException ex) {
                        throw ex;
                     } catch (Throwable ex) {
                        throw new BeanDefinitionStoreException(
                           "Failed to process import candidates for configuration class [" + configurationClass.getMetadata().getClassName() + "]", ex
                        );
                     }
                  }
               );
         }
      }

      private DeferredImportSelector.Group createGroup(@Nullable Class<? extends DeferredImportSelector.Group> type) {
         Class<? extends DeferredImportSelector.Group> effectiveType = type != null ? type : ConfigurationClassParser.DefaultDeferredImportSelectorGroup.class;
         return ParserStrategyUtils.instantiateClass(
            effectiveType,
            DeferredImportSelector.Group.class,
            ConfigurationClassParser.this.environment,
            ConfigurationClassParser.this.resourceLoader,
            ConfigurationClassParser.this.registry
         );
      }
   }

   private class DeferredImportSelectorHandler {
      private @Nullable List<ConfigurationClassParser.DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

      void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
         ConfigurationClassParser.DeferredImportSelectorHolder holder = new ConfigurationClassParser.DeferredImportSelectorHolder(configClass, importSelector);
         if (this.deferredImportSelectors == null) {
            ConfigurationClassParser.DeferredImportSelectorGroupingHandler handler = ConfigurationClassParser.this.new DeferredImportSelectorGroupingHandler();
            handler.register(holder);
            handler.processGroupImports();
         } else {
            this.deferredImportSelectors.add(holder);
         }
      }

      void process() {
         List<ConfigurationClassParser.DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
         this.deferredImportSelectors = null;

         try {
            if (deferredImports != null) {
               ConfigurationClassParser.DeferredImportSelectorGroupingHandler handler = ConfigurationClassParser.this.new DeferredImportSelectorGroupingHandler(
                  
               );
               deferredImports.sort(ConfigurationClassParser.DEFERRED_IMPORT_COMPARATOR);
               deferredImports.forEach(handler::register);
               handler.processGroupImports();
            }
         } finally {
            this.deferredImportSelectors = new ArrayList<>();
         }
      }
   }

   private static class DeferredImportSelectorHolder {
      private final ConfigurationClass configurationClass;
      private final DeferredImportSelector importSelector;

      DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
         this.configurationClass = configClass;
         this.importSelector = selector;
      }

      ConfigurationClass getConfigurationClass() {
         return this.configurationClass;
      }

      DeferredImportSelector getImportSelector() {
         return this.importSelector;
      }
   }

   private class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {
      private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap();

      void registerImport(AnnotationMetadata importingClass, String importedClass) {
         this.imports.add(importedClass, importingClass);
      }

      @Override
      public @Nullable AnnotationMetadata getImportingClassFor(String importedClass) {
         return (AnnotationMetadata)CollectionUtils.lastElement((List)this.imports.get(importedClass));
      }

      @Override
      public void removeImportingClass(String importingClass) {
         for (List<AnnotationMetadata> list : this.imports.values()) {
            Iterator<AnnotationMetadata> iterator = list.iterator();

            while (iterator.hasNext()) {
               if (iterator.next().getClassName().equals(importingClass)) {
                  iterator.remove();
                  break;
               }
            }
         }

         ConfigurationClassParser.this.removeKnownSuperclass(importingClass, true);
      }

      @Override
      public String toString() {
         StringJoiner joiner = new StringJoiner("->", "[", "]");

         for (ConfigurationClass configurationClass : this) {
            joiner.add(configurationClass.getSimpleName());
         }

         return joiner.toString();
      }
   }

   private class SourceClass implements Ordered {
      private final Object source;
      private final AnnotationMetadata metadata;

      public SourceClass(Object source) {
         this.source = source;
         if (source instanceof Class<?> sourceClass) {
            this.metadata = AnnotationMetadata.introspect(sourceClass);
         } else {
            this.metadata = ((MetadataReader)source).getAnnotationMetadata();
         }
      }

      public final AnnotationMetadata getMetadata() {
         return this.metadata;
      }

      public int getOrder() {
         Integer order = ConfigurationClassUtils.getOrder(this.metadata);
         return order != null ? order : Integer.MAX_VALUE;
      }

      public Class<?> loadClass() throws ClassNotFoundException {
         if (this.source instanceof Class<?> sourceClass) {
            return sourceClass;
         } else {
            String className = ((MetadataReader)this.source).getClassMetadata().getClassName();
            return ClassUtils.forName(className, ConfigurationClassParser.this.resourceLoader.getClassLoader());
         }
      }

      public boolean isAssignable(Class<?> clazz) throws IOException {
         return this.source instanceof Class<?> sourceClass
            ? clazz.isAssignableFrom(sourceClass)
            : new AssignableTypeFilter(clazz).match((MetadataReader)this.source, ConfigurationClassParser.this.metadataReaderFactory);
      }

      public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
         return this.source instanceof Class<?> sourceClass
            ? new ConfigurationClass(sourceClass, importedBy)
            : new ConfigurationClass((MetadataReader)this.source, importedBy);
      }

      public Collection<ConfigurationClassParser.SourceClass> getMemberClasses() throws IOException {
         Object sourceToProcess = this.source;
         if (sourceToProcess instanceof Class<?> sourceClass) {
            try {
               Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
               List<ConfigurationClassParser.SourceClass> members = new ArrayList<>(declaredClasses.length);

               for (Class<?> declaredClass : declaredClasses) {
                  members.add(ConfigurationClassParser.this.asSourceClass(declaredClass, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER));
               }

               return members;
            } catch (NoClassDefFoundError err) {
               sourceToProcess = ConfigurationClassParser.this.metadataReaderFactory.getMetadataReader(sourceClass.getName());
            }
         }

         MetadataReader sourceReader = (MetadataReader)sourceToProcess;
         String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
         List<ConfigurationClassParser.SourceClass> members = new ArrayList<>(memberClassNames.length);

         for (String memberClassName : memberClassNames) {
            try {
               members.add(ConfigurationClassParser.this.asSourceClass(memberClassName, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER));
            } catch (IOException ex) {
               if (ConfigurationClassParser.this.logger.isDebugEnabled()) {
                  ConfigurationClassParser.this.logger
                     .debug("Failed to resolve member class [" + memberClassName + "] - not considering it as a configuration class candidate");
               }
            }
         }

         return members;
      }

      public ConfigurationClassParser.SourceClass getSuperClass() throws IOException {
         return this.source instanceof Class<?> sourceClass
            ? ConfigurationClassParser.this.asSourceClass(sourceClass.getSuperclass(), ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER)
            : ConfigurationClassParser.this.asSourceClass(
               ((MetadataReader)this.source).getClassMetadata().getSuperClassName(), ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER
            );
      }

      public Set<ConfigurationClassParser.SourceClass> getInterfaces() throws IOException {
         Set<ConfigurationClassParser.SourceClass> result = new LinkedHashSet<>();
         if (this.source instanceof Class<?> sourceClass) {
            for (Class<?> ifcClass : sourceClass.getInterfaces()) {
               result.add(ConfigurationClassParser.this.asSourceClass(ifcClass, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER));
            }
         } else {
            for (String className : this.metadata.getInterfaceNames()) {
               result.add(ConfigurationClassParser.this.asSourceClass(className, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER));
            }
         }

         return result;
      }

      public Set<ConfigurationClassParser.SourceClass> getAnnotations() {
         Set<ConfigurationClassParser.SourceClass> result = new LinkedHashSet<>();
         if (this.source instanceof Class<?> sourceClass) {
            for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
               Class<?> annType = ann.annotationType();
               if (!annType.getName().startsWith("java")) {
                  try {
                     result.add(ConfigurationClassParser.this.asSourceClass(annType, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER));
                  } catch (Throwable var10) {
                  }
               }
            }
         } else {
            for (String className : this.metadata.getAnnotationTypes()) {
               if (!className.startsWith("java")) {
                  try {
                     result.add(this.getRelated(className));
                  } catch (Throwable var9) {
                  }
               }
            }
         }

         return result;
      }

      public Collection<ConfigurationClassParser.SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
         Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
         if (annotationAttributes != null && annotationAttributes.containsKey(attribute)) {
            String[] classNames = (String[])annotationAttributes.get(attribute);
            Set<ConfigurationClassParser.SourceClass> result = CollectionUtils.newLinkedHashSet(classNames.length);

            for (String className : classNames) {
               result.add(this.getRelated(className));
            }

            return result;
         } else {
            return Collections.emptySet();
         }
      }

      private ConfigurationClassParser.SourceClass getRelated(String className) throws IOException {
         if (this.source instanceof Class<?> sourceClass) {
            try {
               Class<?> clazz = ClassUtils.forName(className, sourceClass.getClassLoader());
               return ConfigurationClassParser.this.asSourceClass(clazz, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER);
            } catch (ClassNotFoundException ex) {
               if (className.startsWith("java")) {
                  throw new IOException("Failed to load class [" + className + "]", ex);
               } else {
                  return ConfigurationClassParser.this.new SourceClass(ConfigurationClassParser.this.metadataReaderFactory.getMetadataReader(className));
               }
            }
         } else {
            return ConfigurationClassParser.this.asSourceClass(className, ConfigurationClassParser.DEFAULT_EXCLUSION_FILTER);
         }
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof ConfigurationClassParser.SourceClass that && this.metadata.getClassName().equals(that.metadata.getClassName());
      }

      @Override
      public int hashCode() {
         return this.metadata.getClassName().hashCode();
      }

      @Override
      public String toString() {
         return this.metadata.getClassName();
      }
   }
}
