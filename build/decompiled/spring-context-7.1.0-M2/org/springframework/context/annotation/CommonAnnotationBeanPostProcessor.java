package org.springframework.context.annotation;

import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aot.generate.AccessControl;
import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.support.ClassHintUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.annotation.InjectionMetadata.InjectedElement;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.CodeBlock.Builder;
import org.springframework.jndi.support.SimpleJndiBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public class CommonAnnotationBeanPostProcessor
   extends InitDestroyAnnotationBeanPostProcessor
   implements InstantiationAwareBeanPostProcessor,
   BeanFactoryAware,
   Serializable {
   private static final boolean JNDI_PRESENT = ClassUtils.isPresent("javax.naming.InitialContext", CommonAnnotationBeanPostProcessor.class.getClassLoader());
   private static final @Nullable Class<? extends Annotation> JAKARTA_RESOURCE_TYPE = AnnotationUtils.loadAnnotationType("jakarta.annotation.Resource");
   private static final @Nullable Class<? extends Annotation> EJB_ANNOTATION_TYPE;
   private static final Set<Class<? extends Annotation>> resourceAnnotationTypes = CollectionUtils.newLinkedHashSet(2);
   private final Set<String> ignoredResourceTypes = new HashSet<>(1);
   private boolean fallbackToDefaultTypeMatch = true;
   private boolean alwaysUseJndiLookup = false;
   private transient @Nullable BeanFactory jndiFactory;
   private transient @Nullable BeanFactory resourceFactory;
   private transient @Nullable BeanFactory beanFactory;
   private transient @Nullable StringValueResolver embeddedValueResolver;
   private final transient Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);

   public CommonAnnotationBeanPostProcessor() {
      this.setOrder(2147483644);
      this.addInitAnnotationType(AnnotationUtils.loadAnnotationType("jakarta.annotation.PostConstruct"));
      this.addDestroyAnnotationType(AnnotationUtils.loadAnnotationType("jakarta.annotation.PreDestroy"));
      if (JNDI_PRESENT) {
         this.jndiFactory = new SimpleJndiBeanFactory();
      }
   }

   public void ignoreResourceType(String resourceType) {
      Assert.notNull(resourceType, "Ignored resource type must not be null");
      this.ignoredResourceTypes.add(resourceType);
   }

   public void setFallbackToDefaultTypeMatch(boolean fallbackToDefaultTypeMatch) {
      this.fallbackToDefaultTypeMatch = fallbackToDefaultTypeMatch;
   }

   public void setAlwaysUseJndiLookup(boolean alwaysUseJndiLookup) {
      this.alwaysUseJndiLookup = alwaysUseJndiLookup;
   }

   public void setJndiFactory(BeanFactory jndiFactory) {
      Assert.notNull(jndiFactory, "BeanFactory must not be null");
      this.jndiFactory = jndiFactory;
   }

   public void setResourceFactory(BeanFactory resourceFactory) {
      Assert.notNull(resourceFactory, "BeanFactory must not be null");
      this.resourceFactory = resourceFactory;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
      if (this.resourceFactory == null) {
         this.resourceFactory = beanFactory;
      }

      if (beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
         this.embeddedValueResolver = new EmbeddedValueResolver(configurableBeanFactory);
      }
   }

   public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
      super.postProcessMergedBeanDefinition(beanDefinition, beanType, beanName);
      InjectionMetadata metadata = this.findResourceMetadata(beanName, beanType, null);
      metadata.checkConfigMembers(beanDefinition);
   }

   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      BeanRegistrationAotContribution parentAotContribution = super.processAheadOfTime(registeredBean);
      Class<?> beanClass = registeredBean.getBeanClass();
      String beanName = registeredBean.getBeanName();
      RootBeanDefinition beanDefinition = registeredBean.getMergedBeanDefinition();
      InjectionMetadata metadata = this.findResourceMetadata(beanName, beanClass, beanDefinition.getPropertyValues());
      Collection<CommonAnnotationBeanPostProcessor.LookupElement> injectedElements = this.getInjectedElements(metadata, beanDefinition.getPropertyValues());
      if (!ObjectUtils.isEmpty(injectedElements)) {
         CommonAnnotationBeanPostProcessor.AotContribution aotContribution = new CommonAnnotationBeanPostProcessor.AotContribution(
            beanClass, injectedElements, this.getAutowireCandidateResolver(registeredBean)
         );
         return BeanRegistrationAotContribution.concat(parentAotContribution, aotContribution);
      } else {
         return parentAotContribution;
      }
   }

   private @Nullable AutowireCandidateResolver getAutowireCandidateResolver(RegisteredBean registeredBean) {
      return registeredBean.getBeanFactory() instanceof DefaultListableBeanFactory lbf ? lbf.getAutowireCandidateResolver() : null;
   }

   private Collection<CommonAnnotationBeanPostProcessor.LookupElement> getInjectedElements(InjectionMetadata metadata, PropertyValues propertyValues) {
      return metadata.getInjectedElements(propertyValues);
   }

   public void resetBeanDefinition(String beanName) {
      this.injectionMetadataCache.remove(beanName);
   }

   public @Nullable Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
      return null;
   }

   public boolean postProcessAfterInstantiation(Object bean, String beanName) {
      return true;
   }

   public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
      InjectionMetadata metadata = this.findResourceMetadata(beanName, bean.getClass(), pvs);

      try {
         metadata.inject(bean, beanName, pvs);
         return pvs;
      } catch (Throwable ex) {
         throw new BeanCreationException(beanName, "Injection of resource dependencies failed", ex);
      }
   }

   public void processInjection(Object bean) throws BeanCreationException {
      Class<?> clazz = bean.getClass();
      InjectionMetadata metadata = this.findResourceMetadata(clazz.getName(), clazz, null);

      try {
         metadata.inject(bean, null, null);
      } catch (BeanCreationException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new BeanCreationException("Injection of resource dependencies failed for class [" + clazz + "]", ex);
      }
   }

   private InjectionMetadata findResourceMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
      String cacheKey = StringUtils.hasLength(beanName) ? beanName : clazz.getName();
      InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
      if (InjectionMetadata.needsRefresh(metadata, clazz)) {
         synchronized (this.injectionMetadataCache) {
            metadata = this.injectionMetadataCache.get(cacheKey);
            if (InjectionMetadata.needsRefresh(metadata, clazz)) {
               if (metadata != null) {
                  metadata.clear(pvs);
               }

               metadata = this.buildResourceMetadata(clazz);
               this.injectionMetadataCache.put(cacheKey, metadata);
            }
         }
      }

      return metadata;
   }

   private InjectionMetadata buildResourceMetadata(Class<?> clazz) {
      if (!AnnotationUtils.isCandidateClass(clazz, resourceAnnotationTypes)) {
         return InjectionMetadata.EMPTY;
      }

      List<InjectedElement> elements = new ArrayList<>();
      Class<?> targetClass = ClassUtils.getUserClass(clazz);

      do {
         List<InjectedElement> currElements = new ArrayList<>();
         ReflectionUtils.doWithLocalFields(targetClass, field -> {
            if (EJB_ANNOTATION_TYPE != null && field.isAnnotationPresent(EJB_ANNOTATION_TYPE)) {
               if (Modifier.isStatic(field.getModifiers())) {
                  throw new IllegalStateException("@EJB annotation is not supported on static fields");
               }

               currElements.add(new CommonAnnotationBeanPostProcessor.EjbRefElement(field, field, null));
            } else if (JAKARTA_RESOURCE_TYPE != null && field.isAnnotationPresent(JAKARTA_RESOURCE_TYPE)) {
               if (Modifier.isStatic(field.getModifiers())) {
                  throw new IllegalStateException("@Resource annotation is not supported on static fields");
               }

               if (!this.ignoredResourceTypes.contains(field.getType().getName())) {
                  currElements.add(new CommonAnnotationBeanPostProcessor.ResourceElement(field, field, null));
               }
            }
         });
         ReflectionUtils.doWithLocalMethods(
            targetClass,
            method -> {
               if (!method.isBridge()) {
                  if (EJB_ANNOTATION_TYPE != null && method.isAnnotationPresent(EJB_ANNOTATION_TYPE)) {
                     if (method.equals(BridgeMethodResolver.getMostSpecificMethod(method, clazz))) {
                        if (Modifier.isStatic(method.getModifiers())) {
                           throw new IllegalStateException("@EJB annotation is not supported on static methods");
                        }

                        if (method.getParameterCount() != 1) {
                           throw new IllegalStateException("@EJB annotation requires a single-arg method: " + method);
                        }

                        PropertyDescriptor pd = BeanUtils.findPropertyForMethod(method, clazz);
                        currElements.add(new CommonAnnotationBeanPostProcessor.EjbRefElement(method, method, pd));
                     }
                  } else if (JAKARTA_RESOURCE_TYPE != null
                     && method.isAnnotationPresent(JAKARTA_RESOURCE_TYPE)
                     && method.equals(BridgeMethodResolver.getMostSpecificMethod(method, clazz))) {
                     if (Modifier.isStatic(method.getModifiers())) {
                        throw new IllegalStateException("@Resource annotation is not supported on static methods");
                     }

                     Class<?>[] paramTypes = method.getParameterTypes();
                     if (paramTypes.length != 1) {
                        throw new IllegalStateException("@Resource annotation requires a single-arg method: " + method);
                     }

                     if (!this.ignoredResourceTypes.contains(paramTypes[0].getName())) {
                        PropertyDescriptor pd = BeanUtils.findPropertyForMethod(method, clazz);
                        currElements.add(new CommonAnnotationBeanPostProcessor.ResourceElement(method, method, pd));
                     }
                  }
               }
            }
         );
         elements.addAll(0, currElements);
         targetClass = targetClass.getSuperclass();
      } while (targetClass != null && targetClass != Object.class);

      return InjectionMetadata.forElements(elements, clazz);
   }

   protected Object buildLazyResourceProxy(CommonAnnotationBeanPostProcessor.LookupElement element, @Nullable String requestingBeanName) {
      TargetSource ts = new TargetSource() {
         public Class<?> getTargetClass() {
            return element.lookupType;
         }

         public Object getTarget() {
            return CommonAnnotationBeanPostProcessor.this.getResource(element, requestingBeanName);
         }
      };
      ProxyFactory pf = new ProxyFactory();
      pf.setTargetSource(ts);
      if (element.lookupType.isInterface()) {
         pf.addInterface(element.lookupType);
      }

      ClassLoader classLoader = this.beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory
         ? configurableBeanFactory.getBeanClassLoader()
         : null;
      return pf.getProxy(classLoader);
   }

   protected Object getResource(CommonAnnotationBeanPostProcessor.LookupElement element, @Nullable String requestingBeanName) throws NoSuchBeanDefinitionException {
      String jndiName = null;
      if (StringUtils.hasLength(element.mappedName)) {
         jndiName = element.mappedName;
      } else if (this.alwaysUseJndiLookup) {
         jndiName = element.name;
      }

      if (jndiName != null) {
         if (this.jndiFactory == null) {
            throw new NoSuchBeanDefinitionException(element.lookupType, "No JNDI factory configured - specify the 'jndiFactory' property");
         } else {
            return this.jndiFactory.getBean(jndiName, element.lookupType);
         }
      } else if (this.resourceFactory == null) {
         throw new NoSuchBeanDefinitionException(element.lookupType, "No resource factory configured - specify the 'resourceFactory' property");
      } else {
         return this.autowireResource(this.resourceFactory, element, requestingBeanName);
      }
   }

   protected Object autowireResource(BeanFactory factory, CommonAnnotationBeanPostProcessor.LookupElement element, @Nullable String requestingBeanName) throws NoSuchBeanDefinitionException {
      String name = element.name;
      Object resource;
      Set<String> autowiredBeanNames;
      if (factory instanceof AutowireCapableBeanFactory autowireCapableBeanFactory) {
         if (this.fallbackToDefaultTypeMatch && element.isDefaultName && !factory.containsBean(name)) {
            autowiredBeanNames = new LinkedHashSet<>();
            resource = autowireCapableBeanFactory.resolveDependency(element.getDependencyDescriptor(), requestingBeanName, autowiredBeanNames, null);
            if (resource == null) {
               throw new NoSuchBeanDefinitionException(element.getLookupType(), "No resolvable resource object");
            }
         } else {
            resource = autowireCapableBeanFactory.resolveBeanByName(name, element.getDependencyDescriptor());
            autowiredBeanNames = Collections.singleton(name);
         }
      } else {
         resource = factory.getBean(name, element.lookupType);
         autowiredBeanNames = Collections.singleton(name);
      }

      if (factory instanceof ConfigurableBeanFactory configurableBeanFactory) {
         for (String autowiredBeanName : autowiredBeanNames) {
            if (requestingBeanName != null && configurableBeanFactory.containsBean(autowiredBeanName)) {
               configurableBeanFactory.registerDependentBean(autowiredBeanName, requestingBeanName);
            }
         }
      }

      return resource;
   }

   static {
      if (JAKARTA_RESOURCE_TYPE != null) {
         resourceAnnotationTypes.add(JAKARTA_RESOURCE_TYPE);
      }

      EJB_ANNOTATION_TYPE = AnnotationUtils.loadAnnotationType("jakarta.ejb.EJB");
      if (EJB_ANNOTATION_TYPE != null) {
         resourceAnnotationTypes.add(EJB_ANNOTATION_TYPE);
      }
   }

   private static class AotContribution implements BeanRegistrationAotContribution {
      private static final String REGISTERED_BEAN_PARAMETER = "registeredBean";
      private static final String INSTANCE_PARAMETER = "instance";
      private final Class<?> target;
      private final Collection<CommonAnnotationBeanPostProcessor.LookupElement> lookupElements;
      private final @Nullable AutowireCandidateResolver candidateResolver;

      AotContribution(
         Class<?> target, Collection<CommonAnnotationBeanPostProcessor.LookupElement> lookupElements, @Nullable AutowireCandidateResolver candidateResolver
      ) {
         this.target = target;
         this.lookupElements = lookupElements;
         this.candidateResolver = candidateResolver;
      }

      public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         GeneratedClass generatedClass = generationContext.getGeneratedClasses().addForFeatureComponent("ResourceAutowiring", this.target, type -> {
            type.addJavadoc("Resource autowiring for {@link $T}.", new Object[]{this.target});
            type.addModifiers(new javax.lang.model.element.Modifier[]{javax.lang.model.element.Modifier.PUBLIC});
         });
         GeneratedMethod generateMethod = generatedClass.getMethods().add("apply", method -> {
            method.addJavadoc("Apply resource autowiring.", new Object[0]);
            method.addModifiers(new javax.lang.model.element.Modifier[]{javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.STATIC});
            method.addParameter(RegisteredBean.class, "registeredBean", new javax.lang.model.element.Modifier[0]);
            method.addParameter(this.target, "instance", new javax.lang.model.element.Modifier[0]);
            method.returns(this.target);
            method.addCode(this.generateMethodCode(generatedClass.getName(), generationContext.getRuntimeHints()));
         });
         beanRegistrationCode.addInstancePostProcessor(generateMethod.toMethodReference());
         this.registerHints(generationContext.getRuntimeHints());
      }

      private CodeBlock generateMethodCode(ClassName targetClassName, RuntimeHints hints) {
         Builder code = CodeBlock.builder();

         for (CommonAnnotationBeanPostProcessor.LookupElement lookupElement : this.lookupElements) {
            code.addStatement(this.generateMethodStatementForElement(targetClassName, lookupElement, hints));
         }

         code.addStatement("return $L", new Object[]{"instance"});
         return code.build();
      }

      private CodeBlock generateMethodStatementForElement(
         ClassName targetClassName, CommonAnnotationBeanPostProcessor.LookupElement lookupElement, RuntimeHints hints
      ) {
         Member member = lookupElement.getMember();
         if (member instanceof Field field) {
            return this.generateMethodStatementForField(targetClassName, field, lookupElement, hints);
         } else if (member instanceof Method method) {
            return this.generateMethodStatementForMethod(targetClassName, method, lookupElement, hints);
         } else {
            throw new IllegalStateException("Unsupported member type " + member.getClass().getName());
         }
      }

      private CodeBlock generateMethodStatementForField(
         ClassName targetClassName, Field field, CommonAnnotationBeanPostProcessor.LookupElement lookupElement, RuntimeHints hints
      ) {
         hints.reflection().registerField(field);
         CodeBlock resolver = this.generateFieldResolverCode(field, lookupElement);
         AccessControl accessControl = AccessControl.forMember(field);
         return !accessControl.isAccessibleFrom(targetClassName)
            ? CodeBlock.of("$L.resolveAndSet($L, $L)", new Object[]{resolver, "registeredBean", "instance"})
            : CodeBlock.of("$L.$L = $L.resolve($L)", new Object[]{"instance", field.getName(), resolver, "registeredBean"});
      }

      private CodeBlock generateFieldResolverCode(Field field, CommonAnnotationBeanPostProcessor.LookupElement lookupElement) {
         return lookupElement.isDefaultName
            ? CodeBlock.of("$T.$L($S)", new Object[]{ResourceElementResolver.class, "forField", field.getName()})
            : CodeBlock.of("$T.$L($S, $S)", new Object[]{ResourceElementResolver.class, "forField", field.getName(), lookupElement.getName()});
      }

      private CodeBlock generateMethodStatementForMethod(
         ClassName targetClassName, Method method, CommonAnnotationBeanPostProcessor.LookupElement lookupElement, RuntimeHints hints
      ) {
         CodeBlock resolver = this.generateMethodResolverCode(method, lookupElement);
         AccessControl accessControl = AccessControl.forMember(method);
         if (!accessControl.isAccessibleFrom(targetClassName)) {
            hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
            return CodeBlock.of("$L.resolveAndSet($L, $L)", new Object[]{resolver, "registeredBean", "instance"});
         } else {
            hints.reflection().registerType(method.getDeclaringClass(), new MemberCategory[0]);
            return CodeBlock.of("$L.$L($L.resolve($L))", new Object[]{"instance", method.getName(), resolver, "registeredBean"});
         }
      }

      private CodeBlock generateMethodResolverCode(Method method, CommonAnnotationBeanPostProcessor.LookupElement lookupElement) {
         return lookupElement.isDefaultName
            ? CodeBlock.of("$T.$L($S, $T.class)", new Object[]{ResourceElementResolver.class, "forMethod", method.getName(), lookupElement.getLookupType()})
            : CodeBlock.of(
               "$T.$L($S, $T.class, $S)",
               new Object[]{ResourceElementResolver.class, "forMethod", method.getName(), lookupElement.getLookupType(), lookupElement.getName()}
            );
      }

      private void registerHints(RuntimeHints runtimeHints) {
         this.lookupElements.forEach(lookupElement -> this.registerProxyIfNecessary(runtimeHints, lookupElement.getDependencyDescriptor()));
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

   private class EjbRefElement extends CommonAnnotationBeanPostProcessor.LookupElement {
      private final String beanName;

      public EjbRefElement(Member member, AnnotatedElement ae, @Nullable PropertyDescriptor pd) {
         super(member, pd);
         EJB resource = ae.getAnnotation(EJB.class);
         String resourceBeanName = resource.beanName();
         String resourceName = resource.name();
         this.isDefaultName = !StringUtils.hasLength(resourceName);
         if (this.isDefaultName) {
            resourceName = this.member.getName();
            if (this.member instanceof Method && resourceName.startsWith("set") && resourceName.length() > 3) {
               resourceName = StringUtils.uncapitalizeAsProperty(resourceName.substring(3));
            }
         }

         Class<?> resourceType = resource.beanInterface();
         if (Object.class != resourceType) {
            this.checkResourceType(resourceType);
         } else {
            resourceType = this.getResourceType();
         }

         this.beanName = resourceBeanName;
         this.name = resourceName;
         this.lookupType = resourceType;
         this.mappedName = resource.mappedName();
      }

      protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
         if (StringUtils.hasLength(this.beanName)) {
            if (CommonAnnotationBeanPostProcessor.this.beanFactory != null && CommonAnnotationBeanPostProcessor.this.beanFactory.containsBean(this.beanName)) {
               Object bean = CommonAnnotationBeanPostProcessor.this.beanFactory.getBean(this.beanName, this.lookupType);
               if (requestingBeanName != null && CommonAnnotationBeanPostProcessor.this.beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
                  configurableBeanFactory.registerDependentBean(this.beanName, requestingBeanName);
               }

               return bean;
            }

            if (this.isDefaultName && !StringUtils.hasLength(this.mappedName)) {
               throw new NoSuchBeanDefinitionException(
                  this.beanName, "Cannot resolve 'beanName' in local BeanFactory. Consider specifying a general 'name' value instead."
               );
            }
         }

         return CommonAnnotationBeanPostProcessor.this.getResource(this, requestingBeanName);
      }
   }

   protected abstract static class LookupElement extends InjectedElement {
      protected String name = "";
      protected boolean isDefaultName = false;
      protected Class<?> lookupType = Object.class;
      protected @Nullable String mappedName;

      public LookupElement(Member member, @Nullable PropertyDescriptor pd) {
         super(member, pd);
      }

      public final String getName() {
         return this.name;
      }

      public final Class<?> getLookupType() {
         return this.lookupType;
      }

      public final DependencyDescriptor getDependencyDescriptor() {
         return this.isField
            ? new ResourceElementResolver.LookupDependencyDescriptor((Field)this.member, this.lookupType, this.isLazyLookup())
            : new ResourceElementResolver.LookupDependencyDescriptor((Method)this.member, this.lookupType, this.isLazyLookup());
      }

      boolean isLazyLookup() {
         return false;
      }
   }

   private class ResourceElement extends CommonAnnotationBeanPostProcessor.LookupElement {
      private final boolean lazyLookup;

      public ResourceElement(Member member, AnnotatedElement ae, @Nullable PropertyDescriptor pd) {
         super(member, pd);
         Resource resource = ae.getAnnotation(Resource.class);
         String resourceName = resource.name();
         Class<?> resourceType = resource.type();
         this.isDefaultName = !StringUtils.hasLength(resourceName);
         if (this.isDefaultName) {
            resourceName = this.member.getName();
            if (this.member instanceof Method && resourceName.startsWith("set") && resourceName.length() > 3) {
               resourceName = StringUtils.uncapitalizeAsProperty(resourceName.substring(3));
            }
         } else if (CommonAnnotationBeanPostProcessor.this.embeddedValueResolver != null) {
            resourceName = CommonAnnotationBeanPostProcessor.this.embeddedValueResolver.resolveStringValue(resourceName);
         }

         if (Object.class != resourceType) {
            this.checkResourceType(resourceType);
         } else {
            resourceType = this.getResourceType();
         }

         this.name = resourceName != null ? resourceName : "";
         this.lookupType = resourceType;
         String lookupValue = resource.lookup();
         this.mappedName = StringUtils.hasLength(lookupValue) ? lookupValue : resource.mappedName();
         Lazy lazy = ae.getAnnotation(Lazy.class);
         this.lazyLookup = lazy != null && lazy.value();
      }

      protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
         return this.lazyLookup
            ? CommonAnnotationBeanPostProcessor.this.buildLazyResourceProxy(this, requestingBeanName)
            : CommonAnnotationBeanPostProcessor.this.getResource(this, requestingBeanName);
      }

      @Override
      boolean isLazyLookup() {
         return this.lazyLookup;
      }
   }
}
