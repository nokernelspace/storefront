package org.springframework.beans.factory.annotation;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.AccessControl;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.support.ClassHintUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.aot.AutowiredArgumentsCodeGenerator;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.aot.AutowiredMethodArgumentsResolver;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.CodeWarnings;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class AutowiredAnnotationBeanPostProcessor
   implements SmartInstantiationAwareBeanPostProcessor,
   MergedBeanDefinitionPostProcessor,
   BeanRegistrationAotProcessor,
   PriorityOrdered,
   BeanFactoryAware {
   private static final Constructor<?>[] EMPTY_CONSTRUCTOR_ARRAY = new Constructor[0];
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = CollectionUtils.newLinkedHashSet(4);
   private String requiredParameterName = "required";
   private boolean requiredParameterValue = true;
   private int order = 2147483645;
   private @Nullable ConfigurableListableBeanFactory beanFactory;
   private @Nullable MetadataReaderFactory metadataReaderFactory;
   private final Set<String> lookupMethodsChecked = ConcurrentHashMap.newKeySet(256);
   private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<>(256);
   private final Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);

   public AutowiredAnnotationBeanPostProcessor() {
      this.autowiredAnnotationTypes.add(Autowired.class);
      this.autowiredAnnotationTypes.add(Value.class);
      ClassLoader classLoader = AutowiredAnnotationBeanPostProcessor.class.getClassLoader();

      try {
         this.autowiredAnnotationTypes.add(ClassUtils.forName("jakarta.inject.Inject", classLoader));
         this.logger.trace("'jakarta.inject.Inject' annotation found and supported for autowiring");
      } catch (ClassNotFoundException var3) {
      }
   }

   public void setAutowiredAnnotationType(Class<? extends Annotation> autowiredAnnotationType) {
      Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
      this.autowiredAnnotationTypes.clear();
      this.autowiredAnnotationTypes.add(autowiredAnnotationType);
   }

   public void setAutowiredAnnotationTypes(Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
      Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
      this.autowiredAnnotationTypes.clear();
      this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
   }

   public void setRequiredParameterName(String requiredParameterName) {
      this.requiredParameterName = requiredParameterName;
   }

   public void setRequiredParameterValue(boolean requiredParameterValue) {
      this.requiredParameterValue = requiredParameterValue;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
         this.beanFactory = clbf;
         this.metadataReaderFactory = MetadataReaderFactory.create(clbf.getBeanClassLoader());
      } else {
         throw new IllegalArgumentException("AutowiredAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
      }
   }

   @Override
   public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
      this.findInjectionMetadata(beanName, beanType, beanDefinition);
      if (beanDefinition.isSingleton()) {
         this.candidateConstructorsCache.remove(beanType);
         if (!beanDefinition.hasMethodOverrides()) {
            this.lookupMethodsChecked.remove(beanName);
         }
      }
   }

   @Override
   public void resetBeanDefinition(String beanName) {
      this.lookupMethodsChecked.remove(beanName);
      this.injectionMetadataCache.remove(beanName);
   }

   @Override
   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      Class<?> beanClass = registeredBean.getBeanClass();
      String beanName = registeredBean.getBeanName();
      RootBeanDefinition beanDefinition = registeredBean.getMergedBeanDefinition();
      InjectionMetadata metadata = this.findInjectionMetadata(beanName, beanClass, beanDefinition);
      Collection<AutowiredAnnotationBeanPostProcessor.AutowiredElement> autowiredElements = this.getAutowiredElements(
         metadata, beanDefinition.getPropertyValues()
      );
      return !ObjectUtils.isEmpty(autowiredElements)
         ? new AutowiredAnnotationBeanPostProcessor.AotContribution(beanClass, autowiredElements, this.getAutowireCandidateResolver())
         : null;
   }

   private Collection<AutowiredAnnotationBeanPostProcessor.AutowiredElement> getAutowiredElements(InjectionMetadata metadata, PropertyValues propertyValues) {
      return metadata.getInjectedElements(propertyValues);
   }

   private @Nullable AutowireCandidateResolver getAutowireCandidateResolver() {
      return this.beanFactory instanceof DefaultListableBeanFactory lbf ? lbf.getAutowireCandidateResolver() : null;
   }

   private InjectionMetadata findInjectionMetadata(String beanName, Class<?> beanType, RootBeanDefinition beanDefinition) {
      InjectionMetadata metadata = this.findAutowiringMetadata(beanName, beanType, null);
      metadata.checkConfigMembers(beanDefinition);
      return metadata;
   }

   @Override
   public Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeanCreationException {
      this.checkLookupMethods(beanClass, beanName);
      if (this.beanFactory instanceof AbstractAutowireCapableBeanFactory aacBeanFactory) {
         RootBeanDefinition mbd = (RootBeanDefinition)this.beanFactory.getMergedBeanDefinition(beanName);
         if (mbd.getFactoryMethodName() == null && mbd.hasBeanClass()) {
            return aacBeanFactory.getInstantiationStrategy().getActualBeanClass(mbd, beanName, aacBeanFactory);
         }
      }

      return beanClass;
   }

   @Override
   public Constructor<?> @Nullable [] determineCandidateConstructors(Class<?> beanClass, final String beanName) throws BeanCreationException {
      this.checkLookupMethods(beanClass, beanName);
      Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
      if (candidateConstructors == null) {
         synchronized (this.candidateConstructorsCache) {
            candidateConstructors = this.candidateConstructorsCache.get(beanClass);
            if (candidateConstructors == null) {
               Constructor<?>[] rawCandidates;
               try {
                  rawCandidates = beanClass.getDeclaredConstructors();
               } catch (Throwable ex) {
                  throw new BeanCreationException(
                     beanName,
                     "Resolution of declared constructors on bean Class ["
                        + beanClass.getName()
                        + "] from ClassLoader ["
                        + beanClass.getClassLoader()
                        + "] failed",
                     ex
                  );
               }

               List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
               Constructor<?> requiredConstructor = null;
               Constructor<?> defaultConstructor = null;
               Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass);
               int nonSyntheticConstructors = 0;

               for (Constructor<?> candidate : rawCandidates) {
                  if (!candidate.isSynthetic()) {
                     nonSyntheticConstructors++;
                  } else if (primaryConstructor != null) {
                     continue;
                  }

                  MergedAnnotation<?> ann = this.findAutowiredAnnotation(candidate);
                  if (ann == null) {
                     Class<?> userClass = ClassUtils.getUserClass(beanClass);
                     if (userClass != beanClass) {
                        try {
                           Constructor<?> superCtor = userClass.getDeclaredConstructor(candidate.getParameterTypes());
                           ann = this.findAutowiredAnnotation(superCtor);
                        } catch (NoSuchMethodException var19) {
                        }
                     }
                  }

                  if (ann != null) {
                     if (requiredConstructor != null) {
                        throw new BeanCreationException(
                           beanName,
                           "Invalid autowire-marked constructor: "
                              + candidate
                              + ". Found constructor with 'required' Autowired annotation already: "
                              + requiredConstructor
                        );
                     }

                     boolean required = this.determineRequiredStatus(ann);
                     if (required) {
                        if (!candidates.isEmpty()) {
                           throw new BeanCreationException(
                              beanName,
                              "Invalid autowire-marked constructors: " + candidates + ". Found constructor with 'required' Autowired annotation: " + candidate
                           );
                        }

                        requiredConstructor = candidate;
                     }

                     candidates.add(candidate);
                  } else if (candidate.getParameterCount() == 0) {
                     defaultConstructor = candidate;
                  }
               }

               if (!candidates.isEmpty()) {
                  if (requiredConstructor == null) {
                     if (defaultConstructor != null) {
                        candidates.add(defaultConstructor);
                     } else if (candidates.size() == 1 && this.logger.isInfoEnabled()) {
                        this.logger
                           .info(
                              "Inconsistent constructor declaration on bean with name '"
                                 + beanName
                                 + "': single autowire-marked constructor flagged as optional - this constructor is effectively required since there is no default constructor to fall back to: "
                                 + candidates.get(0)
                           );
                     }
                  }

                  candidateConstructors = candidates.toArray(EMPTY_CONSTRUCTOR_ARRAY);
               } else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
                  candidateConstructors = new Constructor[]{rawCandidates[0]};
               } else if (nonSyntheticConstructors == 2
                  && primaryConstructor != null
                  && defaultConstructor != null
                  && !primaryConstructor.equals(defaultConstructor)) {
                  candidateConstructors = new Constructor[]{primaryConstructor, defaultConstructor};
               } else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
                  candidateConstructors = new Constructor[]{primaryConstructor};
               } else {
                  candidateConstructors = EMPTY_CONSTRUCTOR_ARRAY;
               }

               this.candidateConstructorsCache.put(beanClass, candidateConstructors);
            }
         }
      }

      return candidateConstructors.length > 0 ? candidateConstructors : null;
   }

   private void checkLookupMethods(Class<?> beanClass, final String beanName) throws BeanCreationException {
      if (!this.lookupMethodsChecked.contains(beanName)) {
         if (AnnotationUtils.isCandidateClass(beanClass, Lookup.class)) {
            try {
               Class<?> targetClass = beanClass;

               do {
                  ReflectionUtils.doWithLocalMethods(targetClass, method -> {
                     Lookup lookup = method.getAnnotation(Lookup.class);
                     if (lookup != null) {
                        Assert.state(this.beanFactory != null, "No BeanFactory available");
                        LookupOverride override = new LookupOverride(method, lookup.value());

                        try {
                           RootBeanDefinition mbd = (RootBeanDefinition)this.beanFactory.getMergedBeanDefinition(beanName);
                           mbd.getMethodOverrides().addOverride(override);
                        } catch (NoSuchBeanDefinitionException ex) {
                           throw new BeanCreationException(beanName, "Cannot apply @Lookup to beans without corresponding bean definition");
                        }
                     }
                  });
                  targetClass = targetClass.getSuperclass();
               } while (targetClass != null && targetClass != Object.class);
            } catch (IllegalStateException ex) {
               throw new BeanCreationException(beanName, "Lookup method resolution failed", ex);
            }
         }

         this.lookupMethodsChecked.add(beanName);
      }
   }

   @Override
   public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
      InjectionMetadata metadata = this.findAutowiringMetadata(beanName, bean.getClass(), pvs);

      try {
         metadata.inject(bean, beanName, pvs);
         return pvs;
      } catch (BeanCreationException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
      }
   }

   public void processInjection(Object bean) throws BeanCreationException {
      Class<?> clazz = bean.getClass();
      InjectionMetadata metadata = this.findAutowiringMetadata(clazz.getName(), clazz, null);

      try {
         metadata.inject(bean, null, null);
      } catch (BeanCreationException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new BeanCreationException("Injection of autowired dependencies failed for class [" + clazz + "]", ex);
      }
   }

   private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
      String cacheKey = StringUtils.hasLength(beanName) ? beanName : clazz.getName();
      InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
      if (InjectionMetadata.needsRefresh(metadata, clazz)) {
         synchronized (this.injectionMetadataCache) {
            metadata = this.injectionMetadataCache.get(cacheKey);
            if (InjectionMetadata.needsRefresh(metadata, clazz)) {
               if (metadata != null) {
                  metadata.clear(pvs);
               }

               metadata = this.buildAutowiringMetadata(clazz);
               this.injectionMetadataCache.put(cacheKey, metadata);
            }
         }
      }

      return metadata;
   }

   private InjectionMetadata buildAutowiringMetadata(Class<?> clazz) {
      if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
         return InjectionMetadata.EMPTY;
      }

      List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
      Class<?> targetClass = ClassUtils.getUserClass(clazz);

      do {
         List<InjectionMetadata.InjectedElement> fieldElements = new ArrayList<>();
         ReflectionUtils.doWithLocalFields(targetClass, field -> {
            MergedAnnotation<?> ann = this.findAutowiredAnnotation(field);
            if (ann != null) {
               if (Modifier.isStatic(field.getModifiers())) {
                  if (this.logger.isInfoEnabled()) {
                     this.logger.info("Autowired annotation is not supported on static fields: " + field);
                  }

                  return;
               }

               boolean required = this.determineRequiredStatus(ann);
               fieldElements.add(new AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement(field, required));
            }
         });
         List<InjectionMetadata.InjectedElement> methodElements = new ArrayList<>();
         ReflectionUtils.doWithLocalMethods(targetClass, method -> {
            if (!method.isBridge()) {
               MergedAnnotation<?> ann = this.findAutowiredAnnotation(method);
               if (ann != null && method.equals(BridgeMethodResolver.getMostSpecificMethod(method, clazz))) {
                  if (Modifier.isStatic(method.getModifiers())) {
                     if (this.logger.isInfoEnabled()) {
                        this.logger.info("Autowired annotation is not supported on static methods: " + method);
                     }

                     return;
                  }

                  if (method.getParameterCount() == 0) {
                     if (method.getDeclaringClass().isRecord()) {
                        return;
                     }

                     if (this.logger.isInfoEnabled()) {
                        this.logger.info("Autowired annotation should only be used on methods with parameters: " + method);
                     }
                  }

                  boolean required = this.determineRequiredStatus(ann);
                  PropertyDescriptor pd = BeanUtils.findPropertyForMethod(method, clazz);
                  methodElements.add(new AutowiredAnnotationBeanPostProcessor.AutowiredMethodElement(method, required, pd));
               }
            }
         });
         elements.addAll(0, this.sortMethodElements(methodElements, targetClass));
         elements.addAll(0, fieldElements);
         targetClass = targetClass.getSuperclass();
      } while (targetClass != null && targetClass != Object.class);

      return InjectionMetadata.forElements(elements, clazz);
   }

   private @Nullable MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
      MergedAnnotations annotations = MergedAnnotations.from(ao);

      for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
         MergedAnnotation<?> annotation = annotations.get(type);
         if (annotation.isPresent()) {
            return annotation;
         }
      }

      return null;
   }

   protected boolean determineRequiredStatus(MergedAnnotation<?> ann) {
      Optional<Boolean> requiredAttribute = ann.getValue(this.requiredParameterName, Boolean.class);
      return requiredAttribute.isEmpty() || this.requiredParameterValue == requiredAttribute.get();
   }

   private List<InjectionMetadata.InjectedElement> sortMethodElements(List<InjectionMetadata.InjectedElement> methodElements, Class<?> targetClass) {
      if (this.metadataReaderFactory != null && methodElements.size() > 1) {
         try {
            AnnotationMetadata asm = this.metadataReaderFactory.getMetadataReader(targetClass.getName()).getAnnotationMetadata();
            Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Autowired.class.getName());
            if (asmMethods.size() >= methodElements.size()) {
               List<InjectionMetadata.InjectedElement> candidateMethods = new ArrayList<>(methodElements);
               List<InjectionMetadata.InjectedElement> selectedMethods = new ArrayList<>(asmMethods.size());

               for (MethodMetadata asmMethod : asmMethods) {
                  Iterator<InjectionMetadata.InjectedElement> it = candidateMethods.iterator();

                  while (it.hasNext()) {
                     InjectionMetadata.InjectedElement element = it.next();
                     if (element.getMember().getName().equals(asmMethod.getMethodName())) {
                        selectedMethods.add(element);
                        it.remove();
                        break;
                     }
                  }
               }

               if (selectedMethods.size() == methodElements.size()) {
                  return selectedMethods;
               }
            }
         } catch (IOException ex) {
            this.logger.debug("Failed to read class file via ASM for determining @Autowired method order", ex);
         }
      }

      return methodElements;
   }

   private void registerDependentBeans(@Nullable String beanName, Set<String> autowiredBeanNames) {
      if (beanName != null) {
         for (String autowiredBeanName : autowiredBeanNames) {
            if (this.beanFactory != null && this.beanFactory.containsBean(autowiredBeanName)) {
               this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
            }

            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Autowiring by type from bean name '" + beanName + "' to bean named '" + autowiredBeanName + "'");
            }
         }
      }
   }

   private @Nullable Object resolveCachedArgument(@Nullable String beanName, @Nullable Object cachedArgument) {
      if (cachedArgument instanceof DependencyDescriptor descriptor) {
         Assert.state(this.beanFactory != null, "No BeanFactory available");
         return this.beanFactory.resolveDependency(descriptor, beanName, null, null);
      } else {
         return cachedArgument;
      }
   }

   private static class AotContribution implements BeanRegistrationAotContribution {
      private static final String REGISTERED_BEAN_PARAMETER = "registeredBean";
      private static final String INSTANCE_PARAMETER = "instance";
      private final Class<?> target;
      private final Collection<AutowiredAnnotationBeanPostProcessor.AutowiredElement> autowiredElements;
      private final @Nullable AutowireCandidateResolver candidateResolver;

      AotContribution(
         Class<?> target,
         Collection<AutowiredAnnotationBeanPostProcessor.AutowiredElement> autowiredElements,
         @Nullable AutowireCandidateResolver candidateResolver
      ) {
         this.target = target;
         this.autowiredElements = autowiredElements;
         this.candidateResolver = candidateResolver;
      }

      @Override
      public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         GeneratedClass generatedClass = generationContext.getGeneratedClasses().addForFeatureComponent("Autowiring", this.target, type -> {
            type.addJavadoc("Autowiring for {@link $T}.", new Object[]{this.target});
            type.addModifiers(new javax.lang.model.element.Modifier[]{javax.lang.model.element.Modifier.PUBLIC});
         });
         GeneratedMethod generateMethod = generatedClass.getMethods().add("apply", method -> {
            method.addJavadoc("Apply the autowiring.", new Object[0]);
            method.addModifiers(new javax.lang.model.element.Modifier[]{javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC});
            method.addParameter(RegisteredBean.class, "registeredBean", new javax.lang.model.element.Modifier[0]);
            method.addParameter(this.target, "instance", new javax.lang.model.element.Modifier[0]);
            method.returns(this.target);
            CodeWarnings codeWarnings = new CodeWarnings();
            codeWarnings.detectDeprecation(this.target);
            method.addCode(this.generateMethodCode(codeWarnings, generatedClass.getName(), generationContext.getRuntimeHints()));
            codeWarnings.suppress(method);
         });
         beanRegistrationCode.addInstancePostProcessor(generateMethod.toMethodReference());
         if (this.candidateResolver != null) {
            this.registerHints(generationContext.getRuntimeHints());
         }
      }

      private CodeBlock generateMethodCode(CodeWarnings codeWarnings, ClassName targetClassName, RuntimeHints hints) {
         Builder code = CodeBlock.builder();

         for (AutowiredAnnotationBeanPostProcessor.AutowiredElement autowiredElement : this.autowiredElements) {
            code.addStatement(this.generateMethodStatementForElement(codeWarnings, targetClassName, autowiredElement, hints));
         }

         code.addStatement("return $L", new Object[]{"instance"});
         return code.build();
      }

      private CodeBlock generateMethodStatementForElement(
         CodeWarnings codeWarnings, ClassName targetClassName, AutowiredAnnotationBeanPostProcessor.AutowiredElement autowiredElement, RuntimeHints hints
      ) {
         Member member = autowiredElement.getMember();
         boolean required = autowiredElement.required;
         if (member instanceof Field field) {
            return this.generateMethodStatementForField(codeWarnings, targetClassName, field, required, hints);
         } else if (member instanceof Method method) {
            return this.generateMethodStatementForMethod(codeWarnings, targetClassName, method, required, hints);
         } else {
            throw new IllegalStateException("Unsupported member type " + member.getClass().getName());
         }
      }

      private CodeBlock generateMethodStatementForField(CodeWarnings codeWarnings, ClassName targetClassName, Field field, boolean required, RuntimeHints hints) {
         hints.reflection().registerField(field);
         CodeBlock resolver = CodeBlock.of(
            "$T.$L($S)", new Object[]{AutowiredFieldValueResolver.class, !required ? "forField" : "forRequiredField", field.getName()}
         );
         AccessControl accessControl = AccessControl.forMember(field);
         if (!accessControl.isAccessibleFrom(targetClassName)) {
            return CodeBlock.of("$L.resolveAndSet($L, $L)", new Object[]{resolver, "registeredBean", "instance"});
         }

         codeWarnings.detectDeprecation(field);
         return CodeBlock.of("$L.$L = $L.resolve($L)", new Object[]{"instance", field.getName(), resolver, "registeredBean"});
      }

      private CodeBlock generateMethodStatementForMethod(
         CodeWarnings codeWarnings, ClassName targetClassName, Method method, boolean required, RuntimeHints hints
      ) {
         Builder code = CodeBlock.builder();
         code.add("$T.$L", new Object[]{AutowiredMethodArgumentsResolver.class, !required ? "forMethod" : "forRequiredMethod"});
         code.add("($S", new Object[]{method.getName()});
         if (method.getParameterCount() > 0) {
            codeWarnings.detectDeprecation(method.getParameterTypes());
            code.add(", $L", new Object[]{this.generateParameterTypesCode(method.getParameterTypes())});
         }

         code.add(")", new Object[0]);
         AccessControl accessControl = AccessControl.forMember(method);
         if (!accessControl.isAccessibleFrom(targetClassName)) {
            hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            code.add(".resolveAndInvoke($L, $L)", new Object[]{"registeredBean", "instance"});
         } else {
            codeWarnings.detectDeprecation(method);
            hints.reflection().registerType(method.getDeclaringClass(), new MemberCategory[0]);
            CodeBlock arguments = new AutowiredArgumentsCodeGenerator(this.target, method).generateCode(method.getParameterTypes());
            CodeBlock injectionCode = CodeBlock.of("args -> $L.$L($L)", new Object[]{"instance", method.getName(), arguments});
            code.add(".resolve($L, $L)", new Object[]{"registeredBean", injectionCode});
         }

         return code.build();
      }

      private CodeBlock generateParameterTypesCode(Class<?>[] parameterTypes) {
         return CodeBlock.join(Arrays.stream(parameterTypes).map(parameterType -> CodeBlock.of("$T.class", new Object[]{parameterType})).toList(), ", ");
      }

      private void registerHints(RuntimeHints runtimeHints) {
         this.autowiredElements.forEach(autowiredElement -> {
            boolean required = autowiredElement.required;
            Member member = autowiredElement.getMember();
            if (member instanceof Field field) {
               DependencyDescriptor dependencyDescriptor = new DependencyDescriptor(field, required);
               this.registerProxyIfNecessary(runtimeHints, dependencyDescriptor);
            }

            if (member instanceof Method method) {
               Class<?>[] parameterTypes = method.getParameterTypes();

               for (int i = 0; i < parameterTypes.length; i++) {
                  MethodParameter methodParam = new MethodParameter(method, i);
                  DependencyDescriptor dependencyDescriptor = new DependencyDescriptor(methodParam, required);
                  this.registerProxyIfNecessary(runtimeHints, dependencyDescriptor);
               }
            }
         });
      }

      private void registerProxyIfNecessary(RuntimeHints runtimeHints, DependencyDescriptor dependencyDescriptor) {
         if (this.candidateResolver != null) {
            Class<?> proxyClass = this.candidateResolver.getLazyResolutionProxyClass(dependencyDescriptor, null);
            if (proxyClass != null) {
               ClassHintUtils.registerProxyIfNecessary(proxyClass, runtimeHints);
            }
         }
      }
   }

   private abstract static class AutowiredElement extends InjectionMetadata.InjectedElement {
      protected final boolean required;

      protected AutowiredElement(Member member, @Nullable PropertyDescriptor pd, boolean required) {
         super(member, pd);
         this.required = required;
      }
   }

   private class AutowiredFieldElement extends AutowiredAnnotationBeanPostProcessor.AutowiredElement {
      private volatile boolean cached;
      private volatile @Nullable Object cachedFieldValue;

      public AutowiredFieldElement(Field field, boolean required) {
         super(field, null, required);
      }

      @Override
      protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
         Field field = (Field)this.member;
         Object value;
         if (this.cached) {
            try {
               value = AutowiredAnnotationBeanPostProcessor.this.resolveCachedArgument(beanName, this.cachedFieldValue);
            } catch (BeansException ex) {
               this.cached = false;
               AutowiredAnnotationBeanPostProcessor.this.logger.debug("Failed to resolve cached argument", ex);
               value = this.resolveFieldValue(field, bean, beanName);
            }
         } else {
            value = this.resolveFieldValue(field, bean, beanName);
         }

         if (value != null) {
            ReflectionUtils.makeAccessible(field);
            field.set(bean, value);
         }
      }

      private @Nullable Object resolveFieldValue(Field field, Object bean, @Nullable String beanName) {
         DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
         desc.setContainingClass(bean.getClass());
         Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
         Assert.state(AutowiredAnnotationBeanPostProcessor.this.beanFactory != null, "No BeanFactory available");
         TypeConverter typeConverter = AutowiredAnnotationBeanPostProcessor.this.beanFactory.getTypeConverter();

         Object value;
         try {
            value = AutowiredAnnotationBeanPostProcessor.this.beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
         } catch (BeansException ex) {
            throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
         }

         synchronized (this) {
            if (!this.cached) {
               if (value == null && !this.required) {
                  this.cachedFieldValue = null;
               } else {
                  Object cachedFieldValue = desc;
                  AutowiredAnnotationBeanPostProcessor.this.registerDependentBeans(beanName, autowiredBeanNames);
                  if (value != null && autowiredBeanNames.size() == 1) {
                     String autowiredBeanName = autowiredBeanNames.iterator().next();
                     if (AutowiredAnnotationBeanPostProcessor.this.beanFactory.containsBean(autowiredBeanName)
                        && AutowiredAnnotationBeanPostProcessor.this.beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
                        cachedFieldValue = new AutowiredAnnotationBeanPostProcessor.ShortcutDependencyDescriptor(desc, autowiredBeanName);
                     }
                  }

                  this.cachedFieldValue = cachedFieldValue;
                  this.cached = true;
               }
            }

            return value;
         }
      }
   }

   private class AutowiredMethodElement extends AutowiredAnnotationBeanPostProcessor.AutowiredElement {
      private volatile boolean cached;
      private volatile Object @Nullable [] cachedMethodArguments;

      public AutowiredMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
         super(method, pd, required);
      }

      @Override
      protected void inject(Object bean, String beanName, PropertyValues pvs) throws Throwable {
         if (this.shouldInject(pvs)) {
            Method method = (Method)this.member;
            Object[] arguments;
            if (this.cached) {
               try {
                  arguments = this.resolveCachedArguments(beanName, this.cachedMethodArguments);
               } catch (BeansException ex) {
                  this.cached = false;
                  AutowiredAnnotationBeanPostProcessor.this.logger.debug("Failed to resolve cached argument", ex);
                  arguments = this.resolveMethodArguments(method, bean, beanName);
               }
            } else {
               arguments = this.resolveMethodArguments(method, bean, beanName);
            }

            if (arguments != null) {
               try {
                  ReflectionUtils.makeAccessible(method);
                  method.invoke(bean, arguments);
               } catch (InvocationTargetException ex) {
                  throw ex.getTargetException();
               }
            }
         }
      }

      private Object[] resolveCachedArguments(String beanName, Object[] cachedMethodArguments) {
         if (cachedMethodArguments == null) {
            return null;
         }

         Object[] arguments = new Object[cachedMethodArguments.length];

         for (int i = 0; i < arguments.length; i++) {
            arguments[i] = AutowiredAnnotationBeanPostProcessor.this.resolveCachedArgument(beanName, cachedMethodArguments[i]);
         }

         return arguments;
      }

      private Object[] resolveMethodArguments(Method method, Object bean, String beanName) {
         int argumentCount = method.getParameterCount();
         Object[] arguments = new Object[argumentCount];
         DependencyDescriptor[] descriptors = new DependencyDescriptor[argumentCount];
         Set<String> autowiredBeanNames = CollectionUtils.newLinkedHashSet(argumentCount);
         Assert.state(AutowiredAnnotationBeanPostProcessor.this.beanFactory != null, "No BeanFactory available");
         TypeConverter typeConverter = AutowiredAnnotationBeanPostProcessor.this.beanFactory.getTypeConverter();

         for (int i = 0; i < arguments.length; i++) {
            MethodParameter methodParam = new MethodParameter(method, i);
            DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
            currDesc.setContainingClass(bean.getClass());
            descriptors[i] = currDesc;

            try {
               Object arg = AutowiredAnnotationBeanPostProcessor.this.beanFactory.resolveDependency(currDesc, beanName, autowiredBeanNames, typeConverter);
               if (arg == null && !this.required && !methodParam.isOptional()) {
                  arguments = null;
                  break;
               }

               arguments[i] = arg;
            } catch (BeansException ex) {
               throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
            }
         }

         synchronized (this) {
            if (!this.cached) {
               if (arguments != null) {
                  DependencyDescriptor[] cachedMethodArguments = Arrays.copyOf(descriptors, argumentCount);
                  AutowiredAnnotationBeanPostProcessor.this.registerDependentBeans(beanName, autowiredBeanNames);
                  if (autowiredBeanNames.size() == argumentCount) {
                     Iterator<String> it = autowiredBeanNames.iterator();
                     Class<?>[] paramTypes = method.getParameterTypes();

                     for (int i = 0; i < paramTypes.length; i++) {
                        String autowiredBeanName = it.next();
                        if (arguments[i] != null
                           && AutowiredAnnotationBeanPostProcessor.this.beanFactory.containsBean(autowiredBeanName)
                           && AutowiredAnnotationBeanPostProcessor.this.beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
                           cachedMethodArguments[i] = new AutowiredAnnotationBeanPostProcessor.ShortcutDependencyDescriptor(descriptors[i], autowiredBeanName);
                        }
                     }
                  }

                  this.cachedMethodArguments = cachedMethodArguments;
                  this.cached = true;
               } else {
                  this.cachedMethodArguments = null;
               }
            }

            return arguments;
         }
      }
   }

   private static class ShortcutDependencyDescriptor extends DependencyDescriptor {
      private final String shortcut;

      public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut) {
         super(original);
         this.shortcut = shortcut;
      }

      @Override
      public Object resolveShortcut(BeanFactory beanFactory) {
         return beanFactory.getBean(this.shortcut, this.getDependencyType());
      }
   }
}
