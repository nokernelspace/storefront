package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.function.ThrowingSupplier;

public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory implements AutowireCapableBeanFactory {
   private InstantiationStrategy instantiationStrategy;
   private @Nullable ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
   private boolean allowCircularReferences = true;
   private boolean allowRawInjectionDespiteWrapping = false;
   private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();
   private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();
   private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal("Currently created bean");
   private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();
   private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();
   private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache = new ConcurrentHashMap<>();

   public AbstractAutowireCapableBeanFactory() {
      this.ignoreDependencyInterface(BeanNameAware.class);
      this.ignoreDependencyInterface(BeanFactoryAware.class);
      this.ignoreDependencyInterface(BeanClassLoaderAware.class);
      this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
   }

   public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
      this();
      this.setParentBeanFactory(parentBeanFactory);
   }

   public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
      this.instantiationStrategy = instantiationStrategy;
   }

   public InstantiationStrategy getInstantiationStrategy() {
      return this.instantiationStrategy;
   }

   public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   public @Nullable ParameterNameDiscoverer getParameterNameDiscoverer() {
      return this.parameterNameDiscoverer;
   }

   public void setAllowCircularReferences(boolean allowCircularReferences) {
      this.allowCircularReferences = allowCircularReferences;
   }

   public boolean isAllowCircularReferences() {
      return this.allowCircularReferences;
   }

   public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
      this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
   }

   public boolean isAllowRawInjectionDespiteWrapping() {
      return this.allowRawInjectionDespiteWrapping;
   }

   public void ignoreDependencyType(Class<?> type) {
      this.ignoredDependencyTypes.add(type);
   }

   public void ignoreDependencyInterface(Class<?> ifc) {
      this.ignoredDependencyInterfaces.add(ifc);
   }

   @Override
   public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
      super.copyConfigurationFrom(otherFactory);
      if (otherFactory instanceof AbstractAutowireCapableBeanFactory otherAutowireFactory) {
         this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
         this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
         this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
         this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
      }
   }

   @Override
   public <T> T createBean(Class<T> beanClass) throws BeansException {
      RootBeanDefinition bd = new AbstractAutowireCapableBeanFactory.CreateFromClassBeanDefinition(beanClass);
      bd.setScope("prototype");
      bd.allowCaching = ClassUtils.isCacheSafe(beanClass, this.getBeanClassLoader());
      return (T)this.createBean(beanClass.getName(), bd, null);
   }

   @Override
   public void autowireBean(Object existingBean) {
      RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
      bd.setScope("prototype");
      bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), this.getBeanClassLoader());
      BeanWrapper bw = new BeanWrapperImpl(existingBean);
      this.initBeanWrapper(bw);
      this.populateBean(bd.getBeanClass().getName(), bd, bw);
   }

   @Override
   public Object configureBean(Object existingBean, String beanName) throws BeansException {
      this.markBeanAsCreated(beanName);
      BeanDefinition mbd = this.getMergedBeanDefinition(beanName);
      RootBeanDefinition bd = null;
      if (mbd instanceof RootBeanDefinition rbd) {
         bd = rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition();
      }

      if (bd == null) {
         bd = new RootBeanDefinition(mbd);
      }

      if (!bd.isPrototype()) {
         bd.setScope("prototype");
         bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), this.getBeanClassLoader());
      }

      BeanWrapper bw = new BeanWrapperImpl(existingBean);
      this.initBeanWrapper(bw);
      this.populateBean(beanName, bd, bw);
      return this.initializeBean(beanName, existingBean, bd);
   }

   @Deprecated(since = "6.1")
   @Override
   public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
      RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
      bd.setScope("prototype");
      return this.createBean(beanClass.getName(), bd, null);
   }

   @Override
   public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
      RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
      bd.setScope("prototype");
      if (bd.getResolvedAutowireMode() == 3) {
         return this.autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
      }

      Object bean = this.getInstantiationStrategy().instantiate(bd, null, this);
      this.populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
      return bean;
   }

   @Override
   public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck) throws BeansException {
      if (autowireMode == 3) {
         throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
      }

      RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
      bd.setScope("prototype");
      BeanWrapper bw = new BeanWrapperImpl(existingBean);
      this.initBeanWrapper(bw);
      this.populateBean(bd.getBeanClass().getName(), bd, bw);
   }

   @Override
   public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
      this.markBeanAsCreated(beanName);
      BeanDefinition bd = this.getMergedBeanDefinition(beanName);
      BeanWrapper bw = new BeanWrapperImpl(existingBean);
      this.initBeanWrapper(bw);
      this.applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
   }

   @Override
   public Object initializeBean(Object existingBean, String beanName) {
      return this.initializeBean(beanName, existingBean, null);
   }

   @Deprecated(since = "6.1")
   @Override
   public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName) throws BeansException {
      Object result = existingBean;

      for (BeanPostProcessor processor : this.getBeanPostProcessors()) {
         Object current = processor.postProcessBeforeInitialization(result, beanName);
         if (current == null) {
            return result;
         }

         result = current;
      }

      return result;
   }

   @Deprecated(since = "6.1")
   @Override
   public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName) throws BeansException {
      Object result = existingBean;

      for (BeanPostProcessor processor : this.getBeanPostProcessors()) {
         Object current = processor.postProcessAfterInitialization(result, beanName);
         if (current == null) {
            return result;
         }

         result = current;
      }

      return result;
   }

   @Override
   public void destroyBean(Object existingBean) {
      new DisposableBeanAdapter(existingBean, this.getBeanPostProcessorCache().destructionAware).destroy();
   }

   @Override
   public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
      InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);

      try {
         return this.getBean(name, descriptor.getDependencyType());
      } finally {
         ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
      }
   }

   @Override
   public @Nullable Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
      return this.resolveDependency(descriptor, requestingBeanName, null, null);
   }

   @Override
   protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object @Nullable [] args) throws BeanCreationException {
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Creating instance of bean '" + beanName + "'");
      }

      RootBeanDefinition mbdToUse = mbd;
      Class<?> resolvedClass = this.resolveBeanClass(mbd, beanName);
      if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
         mbdToUse = new RootBeanDefinition(mbd);
         mbdToUse.setBeanClass(resolvedClass);

         try {
            mbdToUse.prepareMethodOverrides();
         } catch (BeanDefinitionValidationException ex) {
            throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(), beanName, "Validation of method overrides failed", ex);
         }
      }

      try {
         Object bean = this.resolveBeforeInstantiation(beanName, mbdToUse);
         if (bean != null) {
            return bean;
         }
      } catch (Throwable ex) {
         throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, "BeanPostProcessor before instantiation of bean failed", ex);
      }

      try {
         Object beanInstance = this.doCreateBean(beanName, mbdToUse, args);
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Finished creating instance of bean '" + beanName + "'");
         }

         return beanInstance;
      } catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
      }
   }

   protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object @Nullable [] args) throws BeanCreationException {
      BeanWrapper instanceWrapper = null;
      if (mbd.isSingleton()) {
         instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
      }

      if (instanceWrapper == null) {
         instanceWrapper = this.createBeanInstance(beanName, mbd, args);
      }

      Object bean = instanceWrapper.getWrappedInstance();
      Class<?> beanType = instanceWrapper.getWrappedClass();
      if (beanType != NullBean.class) {
         mbd.resolvedTargetType = beanType;
      }

      synchronized (mbd.postProcessingLock) {
         if (!mbd.postProcessed) {
            try {
               this.applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
            } catch (Throwable ex) {
               throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Post-processing of merged bean definition failed", ex);
            }

            mbd.markAsPostProcessed();
         }
      }

      boolean earlySingletonExposure = mbd.isSingleton() && this.allowCircularReferences && this.isSingletonCurrentlyInCreation(beanName);
      if (earlySingletonExposure) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Eagerly caching bean '" + beanName + "' to allow for resolving potential circular references");
         }

         this.addSingletonFactory(beanName, () -> this.getEarlyBeanReference(beanName, mbd, bean));
      }

      Object exposedObject = bean;

      try {
         this.populateBean(beanName, mbd, instanceWrapper);
         exposedObject = this.initializeBean(beanName, exposedObject, mbd);
      } catch (Throwable ex) {
         if (ex instanceof BeanCreationException bce && beanName.equals(bce.getBeanName())) {
            throw bce;
         }

         throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
      }

      if (earlySingletonExposure) {
         Object earlySingletonReference = this.getSingleton(beanName, false);
         if (earlySingletonReference != null) {
            if (exposedObject == bean) {
               exposedObject = earlySingletonReference;
            } else if (!this.allowRawInjectionDespiteWrapping && this.hasDependentBean(beanName)) {
               String[] dependentBeans = this.getDependentBeans(beanName);
               Set<String> actualDependentBeans = CollectionUtils.newLinkedHashSet(dependentBeans.length);

               for (String dependentBean : dependentBeans) {
                  if (!this.removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                     actualDependentBeans.add(dependentBean);
                  }
               }

               if (!actualDependentBeans.isEmpty()) {
                  throw new BeanCurrentlyInCreationException(
                     beanName,
                     "Bean with name '"
                        + beanName
                        + "' has been injected into other beans ["
                        + StringUtils.collectionToCommaDelimitedString(actualDependentBeans)
                        + "] in its raw version as part of a circular reference, but has eventually been wrapped. This means that said other beans do not use the final version of the bean. This is often the result of over-eager type matching - consider using 'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example."
                  );
               }
            }
         }
      }

      try {
         this.registerDisposableBeanIfNecessary(beanName, bean, mbd);
         return exposedObject;
      } catch (BeanDefinitionValidationException ex) {
         throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
      }
   }

   @Override
   protected @Nullable Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
      Class<?> targetType = this.determineTargetType(beanName, mbd, typesToMatch);
      if (targetType != null && !mbd.isSynthetic() && this.hasInstantiationAwareBeanPostProcessors()) {
         boolean matchingOnlyFactoryBean = typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class;

         for (SmartInstantiationAwareBeanPostProcessor bp : this.getBeanPostProcessorCache().smartInstantiationAware) {
            Class<?> predicted = bp.predictBeanType(targetType, beanName);
            if (predicted != null && (!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
               return predicted;
            }
         }
      }

      return targetType;
   }

   protected @Nullable Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
      Class<?> targetType = mbd.getTargetType();
      if (targetType == null) {
         if (mbd.getFactoryMethodName() != null) {
            targetType = this.getTypeForFactoryMethod(beanName, mbd, typesToMatch);
         } else {
            targetType = this.resolveBeanClass(mbd, beanName, typesToMatch);
            if (mbd.hasBeanClass()) {
               targetType = this.getInstantiationStrategy().getActualBeanClass(mbd, beanName, this);
            }
         }

         if (ObjectUtils.isEmpty(typesToMatch) || this.getTempClassLoader() == null) {
            mbd.resolvedTargetType = targetType;
         }
      }

      return targetType;
   }

   protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
      ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
      if (cachedReturnType != null) {
         return cachedReturnType.resolve();
      }

      Class<?> commonType = null;
      Method uniqueCandidate = mbd.factoryMethodToIntrospect;
      if (uniqueCandidate == null) {
         boolean isStatic = true;
         String factoryBeanName = mbd.getFactoryBeanName();
         Class<?> factoryClass;
         if (factoryBeanName != null) {
            if (factoryBeanName.equals(beanName)) {
               throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName, "factory-bean reference points back to the same bean definition");
            }

            factoryClass = this.getType(factoryBeanName);
            isStatic = false;
         } else {
            factoryClass = this.resolveBeanClass(mbd, beanName, typesToMatch);
         }

         if (factoryClass == null) {
            return null;
         }

         factoryClass = ClassUtils.getUserClass(factoryClass);
         int minNrOfArgs = mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0;
         Method[] candidates = this.factoryMethodCandidateCache
            .computeIfAbsent(factoryClass, clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

         for (Method candidate : candidates) {
            if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) && candidate.getParameterCount() >= minNrOfArgs) {
               if (candidate.getTypeParameters().length > 0) {
                  try {
                     ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
                     Class<?>[] paramTypes = candidate.getParameterTypes();
                     String[] paramNames = null;
                     if (cav.containsNamedArgument()) {
                        ParameterNameDiscoverer pnd = this.getParameterNameDiscoverer();
                        if (pnd != null) {
                           paramNames = pnd.getParameterNames(candidate);
                        }
                     }

                     Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = CollectionUtils.newHashSet(paramTypes.length);
                     Object[] args = new Object[paramTypes.length];

                     for (int i = 0; i < args.length; i++) {
                        ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
                           i, paramTypes[i], paramNames != null ? paramNames[i] : null, usedValueHolders
                        );
                        if (valueHolder == null) {
                           valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
                        }

                        if (valueHolder != null) {
                           args[i] = valueHolder.getValue();
                           usedValueHolders.add(valueHolder);
                        }
                     }

                     Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(candidate, args, this.getBeanClassLoader());
                     uniqueCandidate = commonType == null && returnType == candidate.getReturnType() ? candidate : null;
                     commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
                     if (commonType == null) {
                        return null;
                     }
                  } catch (Throwable ex) {
                     if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Failed to resolve generic return type for factory method: " + ex);
                     }
                  }
               } else {
                  uniqueCandidate = commonType == null ? candidate : null;
                  commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
                  if (commonType == null) {
                     return null;
                  }
               }
            }
         }

         mbd.factoryMethodToIntrospect = uniqueCandidate;
         if (commonType == null) {
            return null;
         }
      }

      try {
         cachedReturnType = uniqueCandidate != null ? ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType);
         mbd.factoryMethodReturnType = cachedReturnType;
         return cachedReturnType.resolve();
      } catch (LinkageError err) {
         if (this.logger.isDebugEnabled()) {
            this.logger
               .debug("Failed to resolve type for factory method of bean '" + beanName + "': " + (uniqueCandidate != null ? uniqueCandidate : commonType), err);
         }

         return null;
      }
   }

   @Override
   protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
      try {
         ResolvableType result = this.getTypeForFactoryBeanFromAttributes(mbd);
         if (result != ResolvableType.NONE) {
            return result;
         }
      } catch (IllegalArgumentException ex) {
         throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName, String.valueOf(ex.getMessage()));
      }

      if (mbd.getInstanceSupplier() != null) {
         ResolvableType var12 = this.getFactoryBeanGeneric(mbd.targetType);
         if (var12.resolve() != null) {
            return var12;
         }

         var12 = this.getFactoryBeanGeneric(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : null);
         if (var12.resolve() != null) {
            return var12;
         }
      }

      String factoryBeanName = mbd.getFactoryBeanName();
      String factoryMethodName = mbd.getFactoryMethodName();
      if (factoryBeanName != null) {
         if (factoryMethodName != null) {
            BeanDefinition factoryBeanDefinition = this.getBeanDefinition(factoryBeanName);
            Class<?> factoryBeanClass;
            if (factoryBeanDefinition instanceof AbstractBeanDefinition abstractBeanDefinition && abstractBeanDefinition.hasBeanClass()) {
               factoryBeanClass = abstractBeanDefinition.getBeanClass();
            } else {
               RootBeanDefinition fbmbd = this.getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
               factoryBeanClass = this.determineTargetType(factoryBeanName, fbmbd);
            }

            if (factoryBeanClass != null) {
               ResolvableType var14 = this.getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
               if (var14.resolve() != null) {
                  return var14;
               }
            }
         }

         if (!this.isBeanEligibleForMetadataCaching(factoryBeanName)) {
            return ResolvableType.NONE;
         }
      }

      if (allowInit) {
         FactoryBean<?> factoryBean = mbd.isSingleton()
            ? this.getSingletonFactoryBeanForTypeCheck(beanName, mbd)
            : this.getNonSingletonFactoryBeanForTypeCheck(beanName, mbd);
         if (factoryBean != null) {
            Class<?> type = this.getTypeForFactoryBean(factoryBean);
            if (type != null) {
               return ResolvableType.forClass(type);
            }

            return super.getTypeForFactoryBean(beanName, mbd, true);
         }
      }

      if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
         return this.getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
      }

      if (mbd.getInstanceSupplier() == null) {
         ResolvableType var15 = this.getFactoryBeanGeneric(mbd.targetType);
         if (var15.resolve() != null) {
            return var15;
         }

         var15 = this.getFactoryBeanGeneric(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : null);
         if (var15.resolve() != null) {
            return var15;
         }
      }

      return ResolvableType.NONE;
   }

   private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
      Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
      AbstractAutowireCapableBeanFactory.FactoryBeanMethodTypeFinder finder = new AbstractAutowireCapableBeanFactory.FactoryBeanMethodTypeFinder(
         factoryMethodName
      );
      ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
      return finder.getResult();
   }

   protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
      Object exposedObject = bean;
      if (!mbd.isSynthetic() && this.hasInstantiationAwareBeanPostProcessors()) {
         for (SmartInstantiationAwareBeanPostProcessor bp : this.getBeanPostProcessorCache().smartInstantiationAware) {
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
         }
      }

      return exposedObject;
   }

   private @Nullable FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
      Boolean lockFlag = this.isCurrentThreadAllowedToHoldSingletonLock();
      if (lockFlag == null) {
         this.singletonLock.lock();
      } else {
         boolean locked = lockFlag && this.singletonLock.tryLock();
         if (!locked) {
            this.resolveBeanClass(mbd, beanName);
            return null;
         }
      }

      try {
         BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
         if (bw != null) {
            return (FactoryBean<?>)bw.getWrappedInstance();
         } else if (this.getSingleton(beanName, false) instanceof FactoryBean<?> factoryBean) {
            return factoryBean;
         } else if (!this.isSingletonCurrentlyInCreation(beanName)
            && (mbd.getFactoryBeanName() == null || !this.isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
            Object instance;
            try {
               this.beforeSingletonCreation(beanName);
               instance = this.resolveBeforeInstantiation(beanName, mbd);
               if (instance == null) {
                  bw = this.createBeanInstance(beanName, mbd, null);
                  instance = bw.getWrappedInstance();
                  this.factoryBeanInstanceCache.put(beanName, bw);
               }
            } catch (UnsatisfiedDependencyException ex) {
               throw ex;
            } catch (BeanCreationException ex) {
               if (ex.contains(LinkageError.class)) {
                  throw ex;
               }

               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
               }

               this.onSuppressedException(ex);
               return null;
            } finally {
               this.afterSingletonCreation(beanName);
            }

            return this.getFactoryBean(beanName, instance);
         } else {
            return null;
         }
      } finally {
         this.singletonLock.unlock();
      }
   }

   private @Nullable FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
      if (this.isPrototypeCurrentlyInCreation(beanName)) {
         return null;
      }

      Object instance;
      try {
         this.beforePrototypeCreation(beanName);
         instance = this.resolveBeforeInstantiation(beanName, mbd);
         if (instance == null) {
            BeanWrapper bw = this.createBeanInstance(beanName, mbd, null);
            instance = bw.getWrappedInstance();
         }
      } catch (UnsatisfiedDependencyException ex) {
         throw ex;
      } catch (BeanCreationException ex) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
         }

         this.onSuppressedException(ex);
         return null;
      } finally {
         this.afterPrototypeCreation(beanName);
      }

      return this.getFactoryBean(beanName, instance);
   }

   protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
      for (MergedBeanDefinitionPostProcessor processor : this.getBeanPostProcessorCache().mergedDefinition) {
         processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
      }
   }

   protected @Nullable Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
      Object bean = null;
      if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
         if (!mbd.isSynthetic() && this.hasInstantiationAwareBeanPostProcessors()) {
            Class<?> targetType = this.determineTargetType(beanName, mbd);
            if (targetType != null) {
               bean = this.applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
               if (bean != null) {
                  bean = this.applyBeanPostProcessorsAfterInitialization(bean, beanName);
               }
            }
         }

         mbd.beforeInstantiationResolved = bean != null;
      }

      return bean;
   }

   protected @Nullable Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
      for (InstantiationAwareBeanPostProcessor bp : this.getBeanPostProcessorCache().instantiationAware) {
         Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
         if (result != null) {
            return result;
         }
      }

      return null;
   }

   protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object @Nullable [] args) {
      Class<?> beanClass = this.resolveBeanClass(mbd, beanName);
      if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
         throw new BeanCreationException(
            mbd.getResourceDescription(), beanName, "Bean class isn't public, and non-public access not allowed: " + beanClass.getName()
         );
      }

      if (args == null) {
         Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
         if (instanceSupplier != null) {
            return this.obtainFromSupplier(instanceSupplier, beanName, mbd);
         }
      }

      if (mbd.getFactoryMethodName() != null) {
         return this.instantiateUsingFactoryMethod(beanName, mbd, args);
      }

      boolean resolved = false;
      boolean autowireNecessary = false;
      if (args == null) {
         synchronized (mbd.constructorArgumentLock) {
            if (mbd.resolvedConstructorOrFactoryMethod != null) {
               resolved = true;
               autowireNecessary = mbd.constructorArgumentsResolved;
            }
         }
      }

      if (resolved) {
         return autowireNecessary ? this.autowireConstructor(beanName, mbd, null, null) : this.instantiateBean(beanName, mbd);
      } else {
         Constructor<?>[] ctors = this.determineConstructorsFromBeanPostProcessors(beanClass, beanName);
         if (ctors == null && mbd.getResolvedAutowireMode() != 3 && !mbd.hasConstructorArgumentValues() && ObjectUtils.isEmpty(args)) {
            ctors = mbd.getPreferredConstructors();
            return ctors != null ? this.autowireConstructor(beanName, mbd, ctors, null) : this.instantiateBean(beanName, mbd);
         } else {
            return this.autowireConstructor(beanName, mbd, ctors, args);
         }
      }
   }

   private BeanWrapper obtainFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd) {
      String outerBean = (String)this.currentlyCreatedBean.get();
      this.currentlyCreatedBean.set(beanName);

      Object instance;
      try {
         instance = this.obtainInstanceFromSupplier(supplier, beanName, mbd);
      } catch (Throwable ex) {
         if (ex instanceof BeanCreationException bce && beanName.equals(bce.getBeanName())) {
            throw bce;
         }

         throw new BeanCreationException(beanName, "Instantiation of supplied bean failed", ex);
      } finally {
         if (outerBean != null) {
            this.currentlyCreatedBean.set(outerBean);
         } else {
            this.currentlyCreatedBean.remove();
         }
      }

      if (instance == null) {
         instance = new NullBean();
      }

      BeanWrapper bw = new BeanWrapperImpl(instance);
      this.initBeanWrapper(bw);
      return bw;
   }

   protected @Nullable Object obtainInstanceFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd) throws Exception {
      return supplier instanceof ThrowingSupplier<?> throwingSupplier ? throwingSupplier.getWithException() : supplier.get();
   }

   @Override
   protected Object getObjectForBeanInstance(
      Object beanInstance, @Nullable Class<?> requiredType, String name, String beanName, @Nullable RootBeanDefinition mbd
   ) {
      String currentlyCreatedBean = (String)this.currentlyCreatedBean.get();
      if (currentlyCreatedBean != null) {
         this.registerDependentBean(beanName, currentlyCreatedBean);
      }

      return super.getObjectForBeanInstance(beanInstance, requiredType, name, beanName, mbd);
   }

   protected Constructor<?> @Nullable [] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName) throws BeansException {
      if (beanClass != null && this.hasInstantiationAwareBeanPostProcessors()) {
         for (SmartInstantiationAwareBeanPostProcessor bp : this.getBeanPostProcessorCache().smartInstantiationAware) {
            Constructor<?>[] ctors = bp.determineCandidateConstructors(beanClass, beanName);
            if (ctors != null) {
               return ctors;
            }
         }
      }

      return null;
   }

   protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
      try {
         Object beanInstance = this.getInstantiationStrategy().instantiate(mbd, beanName, this);
         BeanWrapper bw = new BeanWrapperImpl(beanInstance);
         this.initBeanWrapper(bw);
         return bw;
      } catch (Throwable ex) {
         throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
      }
   }

   protected BeanWrapper instantiateUsingFactoryMethod(String beanName, RootBeanDefinition mbd, @Nullable Object @Nullable [] explicitArgs) {
      return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
   }

   protected BeanWrapper autowireConstructor(
      String beanName, RootBeanDefinition mbd, Constructor<?> @Nullable [] ctors, @Nullable Object @Nullable [] explicitArgs
   ) {
      return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
   }

   protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
      if (bw == null) {
         if (mbd.hasPropertyValues()) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
         }
      } else if (bw.getWrappedClass().isRecord()) {
         if (mbd.hasPropertyValues()) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Cannot apply property values to a record");
         }
      } else {
         if (!mbd.isSynthetic() && this.hasInstantiationAwareBeanPostProcessors()) {
            for (InstantiationAwareBeanPostProcessor bp : this.getBeanPostProcessorCache().instantiationAware) {
               if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                  return;
               }
            }
         }

         PropertyValues pvs = mbd.hasPropertyValues() ? mbd.getPropertyValues() : null;
         int resolvedAutowireMode = mbd.getResolvedAutowireMode();
         if (resolvedAutowireMode == 1 || resolvedAutowireMode == 2) {
            MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
            if (resolvedAutowireMode == 1) {
               this.autowireByName(beanName, mbd, bw, newPvs);
            }

            if (resolvedAutowireMode == 2) {
               this.autowireByType(beanName, mbd, bw, newPvs);
            }

            pvs = newPvs;
         }

         if (this.hasInstantiationAwareBeanPostProcessors()) {
            if (pvs == null) {
               pvs = mbd.getPropertyValues();
            }

            for (InstantiationAwareBeanPostProcessor bp : this.getBeanPostProcessorCache().instantiationAware) {
               PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
               if (pvsToUse == null) {
                  return;
               }

               pvs = pvsToUse;
            }
         }

         boolean needsDepCheck = mbd.getDependencyCheck() != 0;
         if (needsDepCheck) {
            PropertyDescriptor[] filteredPds = this.filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
            this.checkDependencies(beanName, mbd, filteredPds, pvs);
         }

         if (pvs != null) {
            this.applyPropertyValues(beanName, mbd, bw, pvs);
         }
      }
   }

   protected void autowireByName(String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
      String[] propertyNames = this.unsatisfiedNonSimpleProperties(mbd, bw);

      for (String propertyName : propertyNames) {
         if (this.containsBean(propertyName)) {
            Object bean = this.getBean(propertyName);
            pvs.add(propertyName, bean);
            this.registerDependentBean(propertyName, beanName);
            if (this.logger.isTraceEnabled()) {
               this.logger
                  .trace("Added autowiring by name from bean name '" + beanName + "' via property '" + propertyName + "' to bean named '" + propertyName + "'");
            }
         } else if (this.logger.isTraceEnabled()) {
            this.logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName + "' by name: no matching bean found");
         }
      }
   }

   protected void autowireByType(String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {
      TypeConverter converter = this.getCustomTypeConverter();
      if (converter == null) {
         converter = bw;
      }

      String[] propertyNames = this.unsatisfiedNonSimpleProperties(mbd, bw);
      Set<String> autowiredBeanNames = new LinkedHashSet<>(propertyNames.length * 2);

      for (String propertyName : propertyNames) {
         try {
            PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
            if (Object.class != pd.getPropertyType()) {
               MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
               boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
               DependencyDescriptor desc = new AbstractAutowireCapableBeanFactory.AutowireByTypeDependencyDescriptor(methodParam, eager);
               Object autowiredArgument = this.resolveDependency(desc, beanName, autowiredBeanNames, converter);
               if (autowiredArgument != null) {
                  pvs.add(propertyName, autowiredArgument);
               }

               for (String autowiredBeanName : autowiredBeanNames) {
                  this.registerDependentBean(autowiredBeanName, beanName);
                  if (this.logger.isTraceEnabled()) {
                     this.logger
                        .trace(
                           "Autowiring by type from bean name '" + beanName + "' via property '" + propertyName + "' to bean named '" + autowiredBeanName + "'"
                        );
                  }
               }

               autowiredBeanNames.clear();
            }
         } catch (BeansException ex) {
            throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
         }
      }
   }

   protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
      Set<String> result = new TreeSet<>();
      PropertyValues pvs = mbd.getPropertyValues();
      PropertyDescriptor[] pds = bw.getPropertyDescriptors();

      for (PropertyDescriptor pd : pds) {
         if (pd.getWriteMethod() != null
            && !this.isExcludedFromDependencyCheck(pd)
            && !pvs.contains(pd.getName())
            && !BeanUtils.isSimpleProperty(pd.getPropertyType())) {
            result.add(pd.getName());
         }
      }

      return StringUtils.toStringArray(result);
   }

   protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
      PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
      if (filtered == null) {
         filtered = this.filterPropertyDescriptorsForDependencyCheck(bw);
         if (cache) {
            PropertyDescriptor[] existing = this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
            if (existing != null) {
               filtered = existing;
            }
         }
      }

      return filtered;
   }

   protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
      List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
      pds.removeIf(this::isExcludedFromDependencyCheck);
      return pds.toArray(new PropertyDescriptor[0]);
   }

   protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
      return AutowireUtils.isExcludedFromDependencyCheck(pd)
         || this.ignoredDependencyTypes.contains(pd.getPropertyType())
         || AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces);
   }

   protected void checkDependencies(String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs) throws UnsatisfiedDependencyException {
      int dependencyCheck = mbd.getDependencyCheck();

      for (PropertyDescriptor pd : pds) {
         if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
            boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
            boolean unsatisfied = dependencyCheck == 3 || isSimple && dependencyCheck == 2 || !isSimple && dependencyCheck == 1;
            if (unsatisfied) {
               throw new UnsatisfiedDependencyException(
                  mbd.getResourceDescription(), beanName, pd.getName(), "Set this property value or disable dependency checking for this bean."
               );
            }
         }
      }
   }

   protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
      if (!pvs.isEmpty()) {
         MutablePropertyValues mpvs = null;
         List<PropertyValue> original;
         if (pvs instanceof MutablePropertyValues _mpvs) {
            mpvs = _mpvs;
            if (mpvs.isConverted()) {
               try {
                  bw.setPropertyValues(mpvs);
                  return;
               } catch (BeansException ex) {
                  throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Error setting property values", ex);
               }
            }

            original = mpvs.getPropertyValueList();
         } else {
            original = Arrays.asList(pvs.getPropertyValues());
         }

         TypeConverter converter = this.getCustomTypeConverter();
         if (converter == null) {
            converter = bw;
         }

         BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);
         List<PropertyValue> deepCopy = new ArrayList<>(original.size());
         boolean resolveNecessary = false;

         for (PropertyValue pv : original) {
            if (pv.isConverted()) {
               deepCopy.add(pv);
            } else {
               String propertyName = pv.getName();
               Object originalValue = pv.getValue();
               if (originalValue == AutowiredPropertyMarker.INSTANCE) {
                  Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
                  if (writeMethod == null) {
                     throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
                  }

                  originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
               }

               Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
               Object convertedValue = resolvedValue;
               boolean convertible = this.isConvertibleProperty(propertyName, bw);
               if (convertible) {
                  convertedValue = this.convertForProperty(resolvedValue, propertyName, bw, converter);
               }

               if (resolvedValue == originalValue) {
                  if (convertible) {
                     pv.setConvertedValue(convertedValue);
                  }

                  deepCopy.add(pv);
               } else if (convertible
                  && originalValue instanceof TypedStringValue typedStringValue
                  && !typedStringValue.isDynamic()
                  && !(convertedValue instanceof Collection)
                  && !ObjectUtils.isArray(convertedValue)) {
                  pv.setConvertedValue(convertedValue);
                  deepCopy.add(pv);
               } else {
                  resolveNecessary = true;
                  deepCopy.add(new PropertyValue(pv, convertedValue));
               }
            }
         }

         if (mpvs != null && !resolveNecessary) {
            mpvs.setConverted();
         }

         try {
            bw.setPropertyValues(new MutablePropertyValues(deepCopy));
         } catch (BeansException ex) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
         }
      }
   }

   private boolean isConvertibleProperty(String propertyName, BeanWrapper bw) {
      try {
         return !isNestedOrIndexedProperty(propertyName) && BeanUtils.hasUniqueWriteMethod(bw.getPropertyDescriptor(propertyName));
      } catch (InvalidPropertyException ex) {
         return false;
      }
   }

   private static boolean isNestedOrIndexedProperty(String propertyName) {
      for (int i = 0; i < propertyName.length(); i++) {
         char ch = propertyName.charAt(i);
         if (ch == '.' || ch == '[') {
            return true;
         }
      }

      return false;
   }

   private @Nullable Object convertForProperty(@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {
      if (converter instanceof BeanWrapperImpl beanWrapper) {
         return beanWrapper.convertForProperty(value, propertyName);
      } else {
         PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
         MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
         return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
      }
   }

   protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
      if (bean.getClass() == NullBean.class) {
         return bean;
      }

      this.invokeAwareMethods(beanName, bean);
      Object wrappedBean = bean;
      if (mbd == null || !mbd.isSynthetic()) {
         wrappedBean = this.applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
      }

      try {
         this.invokeInitMethods(beanName, wrappedBean, mbd);
      } catch (Throwable ex) {
         throw new BeanCreationException(mbd != null ? mbd.getResourceDescription() : null, beanName, ex.getMessage(), ex);
      }

      if (mbd == null || !mbd.isSynthetic()) {
         wrappedBean = this.applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
      }

      return wrappedBean;
   }

   private void invokeAwareMethods(String beanName, Object bean) {
      if (bean instanceof Aware) {
         if (bean instanceof BeanNameAware beanNameAware) {
            beanNameAware.setBeanName(beanName);
         }

         if (bean instanceof BeanClassLoaderAware beanClassLoaderAware) {
            ClassLoader bcl = this.getBeanClassLoader();
            if (bcl != null) {
               beanClassLoaderAware.setBeanClassLoader(bcl);
            }
         }

         if (bean instanceof BeanFactoryAware beanFactoryAware) {
            beanFactoryAware.setBeanFactory(this);
         }
      }
   }

   protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd) throws Throwable {
      boolean isInitializingBean = bean instanceof InitializingBean;
      if (isInitializingBean && (mbd == null || !mbd.hasAnyExternallyManagedInitMethod("afterPropertiesSet"))) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
         }

         ((InitializingBean)bean).afterPropertiesSet();
      }

      if (mbd != null && bean.getClass() != NullBean.class) {
         String[] initMethodNames = mbd.getInitMethodNames();
         if (initMethodNames != null) {
            for (String initMethodName : initMethodNames) {
               if (StringUtils.hasLength(initMethodName)
                  && (!isInitializingBean || !"afterPropertiesSet".equals(initMethodName))
                  && !mbd.hasAnyExternallyManagedInitMethod(initMethodName)) {
                  this.invokeCustomInitMethod(beanName, bean, mbd, initMethodName);
               }
            }
         }
      }
   }

   protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd, String initMethodName) throws Throwable {
      Class<?> beanClass = bean.getClass();
      MethodDescriptor descriptor = MethodDescriptor.create(beanName, beanClass, initMethodName);
      String methodName = descriptor.methodName();
      Method initMethod = mbd.isNonPublicAccessAllowed()
         ? BeanUtils.findMethod(descriptor.declaringClass(), methodName)
         : ClassUtils.getMethodIfAvailable(beanClass, methodName, new Class[0]);
      if (initMethod == null) {
         if (mbd.isEnforceInitMethod()) {
            throw new BeanDefinitionValidationException("Could not find an init method named '" + methodName + "' on bean with name '" + beanName + "'");
         }

         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No default init method named '" + methodName + "' found on bean with name '" + beanName + "'");
         }
      } else {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Invoking init method '" + methodName + "' on bean with name '" + beanName + "'");
         }

         Method methodToInvoke = ClassUtils.getPubliclyAccessibleMethodIfPossible(initMethod, beanClass);

         try {
            ReflectionUtils.makeAccessible(methodToInvoke);
            methodToInvoke.invoke(bean);
         } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
         }
      }
   }

   @Override
   protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
      return this.applyBeanPostProcessorsAfterInitialization(object, beanName);
   }

   @Override
   protected void removeSingleton(String beanName) {
      super.removeSingleton(beanName);
      this.factoryBeanInstanceCache.remove(beanName);
   }

   @Override
   protected void clearSingletonCache() {
      super.clearSingletonCache();
      this.factoryBeanInstanceCache.clear();
   }

   Log getLogger() {
      return this.logger;
   }

   private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {
      public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
         super(methodParameter, false, eager);
      }

      @Override
      public @Nullable String getDependencyName() {
         return null;
      }
   }

   private static class CreateFromClassBeanDefinition extends RootBeanDefinition {
      public CreateFromClassBeanDefinition(Class<?> beanClass) {
         super(beanClass);
      }

      public CreateFromClassBeanDefinition(AbstractAutowireCapableBeanFactory.CreateFromClassBeanDefinition original) {
         super(original);
      }

      @Override
      public Constructor<?> @Nullable [] getPreferredConstructors() {
         Constructor<?>[] fromAttribute = super.getPreferredConstructors();
         return fromAttribute != null ? fromAttribute : ConstructorResolver.determinePreferredConstructors(this.getBeanClass());
      }

      @Override
      public RootBeanDefinition cloneBeanDefinition() {
         return new AbstractAutowireCapableBeanFactory.CreateFromClassBeanDefinition(this);
      }
   }

   private static class FactoryBeanMethodTypeFinder implements MethodCallback {
      private final String factoryMethodName;
      private ResolvableType result = ResolvableType.NONE;

      FactoryBeanMethodTypeFinder(String factoryMethodName) {
         this.factoryMethodName = factoryMethodName;
      }

      public void doWith(Method method) throws IllegalArgumentException {
         if (this.isFactoryBeanMethod(method)) {
            ResolvableType returnType = ResolvableType.forMethodReturnType(method);
            ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric(new int[0]);
            if (this.result == ResolvableType.NONE) {
               this.result = candidate;
            } else {
               Class<?> resolvedResult = this.result.resolve();
               Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
               if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
                  this.result = ResolvableType.forClass(commonAncestor);
               }
            }
         }
      }

      private boolean isFactoryBeanMethod(Method method) {
         return method.getName().equals(this.factoryMethodName) && FactoryBean.class.isAssignableFrom(method.getReturnType());
      }

      ResolvableType getResult() {
         Class<?> resolved = this.result.resolve();
         boolean foundResult = resolved != null && resolved != Object.class;
         return foundResult ? this.result : ResolvableType.NONE;
      }
   }
}
