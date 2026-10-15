package org.springframework.beans.factory.support;

import jakarta.inject.Provider;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.SpringProperties;
import org.springframework.core.OrderComparator.OrderSourceProvider;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.CompositeIterator;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class DefaultListableBeanFactory
   extends AbstractAutowireCapableBeanFactory
   implements ConfigurableListableBeanFactory,
   BeanDefinitionRegistry,
   Serializable {
   public static final String STRICT_LOCKING_PROPERTY_NAME = "spring.locking.strict";
   private static @Nullable Class<?> jakartaInjectProviderClass;
   private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories;
   private final @Nullable Boolean strictLocking = SpringProperties.checkFlag("spring.locking.strict");
   private @Nullable String serializationId;
   private @Nullable Boolean allowBeanDefinitionOverriding;
   private boolean allowEagerClassLoading = true;
   private @Nullable Executor bootstrapExecutor;
   private @Nullable Comparator<Object> dependencyComparator;
   private AutowireCandidateResolver autowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE;
   private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);
   private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);
   private final Map<String, BeanDefinitionHolder> mergedBeanDefinitionHolders = new ConcurrentHashMap<>(256);
   private final Map<String, Class<?>> primaryBeanNamesWithType = new ConcurrentHashMap<>(16);
   private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);
   private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);
   private volatile List<String> beanDefinitionNames = new ArrayList<>(256);
   private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);
   private volatile String @Nullable [] frozenBeanDefinitionNames;
   private volatile boolean configurationFrozen;
   private volatile @Nullable String mainThreadPrefix;
   private final NamedThreadLocal<DefaultListableBeanFactory.PreInstantiation> preInstantiationThread = new NamedThreadLocal("Pre-instantiation thread marker");

   public DefaultListableBeanFactory() {
   }

   public DefaultListableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
      super(parentBeanFactory);
   }

   public void setSerializationId(@Nullable String serializationId) {
      if (serializationId != null) {
         serializableFactories.put(serializationId, new WeakReference<>(this));
      } else if (this.serializationId != null) {
         serializableFactories.remove(this.serializationId);
      }

      this.serializationId = serializationId;
   }

   public @Nullable String getSerializationId() {
      return this.serializationId;
   }

   public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
      this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
   }

   public boolean isAllowBeanDefinitionOverriding() {
      return !Boolean.FALSE.equals(this.allowBeanDefinitionOverriding);
   }

   public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
      this.allowEagerClassLoading = allowEagerClassLoading;
   }

   public boolean isAllowEagerClassLoading() {
      return this.allowEagerClassLoading;
   }

   @Override
   public void setBootstrapExecutor(@Nullable Executor bootstrapExecutor) {
      this.bootstrapExecutor = bootstrapExecutor;
   }

   @Override
   public @Nullable Executor getBootstrapExecutor() {
      return this.bootstrapExecutor;
   }

   public void setDependencyComparator(@Nullable Comparator<Object> dependencyComparator) {
      this.dependencyComparator = dependencyComparator;
   }

   public @Nullable Comparator<Object> getDependencyComparator() {
      return this.dependencyComparator;
   }

   public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
      Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
      if (autowireCandidateResolver instanceof BeanFactoryAware beanFactoryAware) {
         beanFactoryAware.setBeanFactory(this);
      }

      this.autowireCandidateResolver = autowireCandidateResolver;
   }

   public AutowireCandidateResolver getAutowireCandidateResolver() {
      return this.autowireCandidateResolver;
   }

   @Override
   public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
      super.copyConfigurationFrom(otherFactory);
      if (otherFactory instanceof DefaultListableBeanFactory otherListableFactory) {
         this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
         this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
         this.bootstrapExecutor = otherListableFactory.bootstrapExecutor;
         this.dependencyComparator = otherListableFactory.dependencyComparator;
         this.setAutowireCandidateResolver(otherListableFactory.getAutowireCandidateResolver().cloneIfNecessary());
         this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
      }
   }

   @Override
   public <T> T getBean(Class<T> requiredType) throws BeansException {
      return this.getBean(requiredType, (Object[])null);
   }

   @Override
   public <T> T getBean(Class<T> requiredType, @Nullable Object @Nullable ... args) throws BeansException {
      Assert.notNull(requiredType, "Required type must not be null");
      Object resolved = this.resolveBean(ResolvableType.forRawClass(requiredType), args, false);
      if (resolved == null) {
         throw new NoSuchBeanDefinitionException(requiredType);
      } else {
         return (T)resolved;
      }
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
      Assert.notNull(requiredType, "Required type must not be null");
      return this.getBeanProvider(ResolvableType.forRawClass(requiredType), true);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
      return this.getBeanProvider(requiredType, true);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(ParameterizedTypeReference<T> requiredType) {
      return this.getBeanProvider(ResolvableType.forType(requiredType), true);
   }

   @Override
   public boolean containsBeanDefinition(String beanName) {
      Assert.notNull(beanName, "Bean name must not be null");
      return this.beanDefinitionMap.containsKey(beanName);
   }

   @Override
   public int getBeanDefinitionCount() {
      return this.beanDefinitionMap.size();
   }

   @Override
   public String[] getBeanDefinitionNames() {
      String[] frozenNames = this.frozenBeanDefinitionNames;
      return frozenNames != null ? (String[])frozenNames.clone() : StringUtils.toStringArray(this.beanDefinitionNames);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
      Assert.notNull(requiredType, "Required type must not be null");
      return this.getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
      return new DefaultListableBeanFactory.BeanObjectProvider<T>() {
         @Override
         public T getObject() throws BeansException {
            T resolved = DefaultListableBeanFactory.this.resolveBean(requiredType, null, false);
            if (resolved == null) {
               throw new NoSuchBeanDefinitionException(requiredType);
            } else {
               return resolved;
            }
         }

         @Override
         public T getObject(@Nullable Object... args) throws BeansException {
            T resolved = DefaultListableBeanFactory.this.resolveBean(requiredType, args, false);
            if (resolved == null) {
               throw new NoSuchBeanDefinitionException(requiredType);
            } else {
               return resolved;
            }
         }

         @Override
         public @Nullable T getIfAvailable() throws BeansException {
            try {
               return DefaultListableBeanFactory.this.resolveBean(requiredType, null, false);
            } catch (ScopeNotActiveException ex) {
               return null;
            }
         }

         @Override
         public void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
            T dependency = (T)this.getIfAvailable();
            if (dependency != null) {
               try {
                  dependencyConsumer.accept(dependency);
               } catch (ScopeNotActiveException var4) {
               }
            }
         }

         @Override
         public @Nullable T getIfUnique() throws BeansException {
            try {
               return DefaultListableBeanFactory.this.resolveBean(requiredType, null, true);
            } catch (ScopeNotActiveException ex) {
               return null;
            }
         }

         @Override
         public void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
            T dependency = (T)this.getIfUnique();
            if (dependency != null) {
               try {
                  dependencyConsumer.accept(dependency);
               } catch (ScopeNotActiveException var4) {
               }
            }
         }

         @Override
         public Stream<T> stream() {
            return Arrays.stream(DefaultListableBeanFactory.this.beanNamesForStream(requiredType, true, allowEagerInit))
               .map(name -> (T)DefaultListableBeanFactory.this.resolveBean(name, requiredType))
               .filter(bean -> !(bean instanceof NullBean));
         }

         @Override
         public Stream<T> orderedStream() {
            String[] beanNames = DefaultListableBeanFactory.this.beanNamesForStream(requiredType, true, allowEagerInit);
            if (beanNames.length == 0) {
               return Stream.empty();
            }

            Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);

            for (String beanName : beanNames) {
               Object beanInstance = DefaultListableBeanFactory.this.resolveBean(beanName, requiredType);
               if (!(beanInstance instanceof NullBean)) {
                  matchingBeans.put(beanName, (T)beanInstance);
               }
            }

            Stream<T> stream = matchingBeans.values().stream();
            return stream.sorted(DefaultListableBeanFactory.this.adaptOrderComparator(matchingBeans));
         }

         @Override
         public Stream<T> stream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
            return Arrays.stream(DefaultListableBeanFactory.this.beanNamesForStream(requiredType, includeNonSingletons, allowEagerInit))
               .filter(name -> customFilter.test(DefaultListableBeanFactory.this.getType(name)))
               .map(name -> (T)DefaultListableBeanFactory.this.resolveBean(name, requiredType))
               .filter(bean -> !(bean instanceof NullBean));
         }

         @Override
         public Stream<T> orderedStream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
            String[] beanNames = DefaultListableBeanFactory.this.beanNamesForStream(requiredType, includeNonSingletons, allowEagerInit);
            if (beanNames.length == 0) {
               return Stream.empty();
            }

            Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);

            for (String beanName : beanNames) {
               if (customFilter.test(DefaultListableBeanFactory.this.getType(beanName))) {
                  Object beanInstance = DefaultListableBeanFactory.this.resolveBean(beanName, requiredType);
                  if (!(beanInstance instanceof NullBean)) {
                     matchingBeans.put(beanName, (T)beanInstance);
                  }
               }
            }

            return matchingBeans.values().stream().sorted(DefaultListableBeanFactory.this.adaptOrderComparator(matchingBeans));
         }
      };
   }

   private <T> @Nullable T resolveBean(ResolvableType requiredType, @Nullable Object @Nullable [] args, boolean nonUniqueAsNull) {
      NamedBeanHolder<T> namedBean = this.resolveNamedBean(requiredType, args, nonUniqueAsNull);
      if (namedBean != null) {
         return namedBean.getBeanInstance();
      } else {
         BeanFactory parent = this.getParentBeanFactory();
         if (parent instanceof DefaultListableBeanFactory dlbf) {
            return dlbf.resolveBean(requiredType, args, nonUniqueAsNull);
         } else if (parent != null) {
            ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
            if (args != null) {
               return parentProvider.getObject(args);
            } else {
               return nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable();
            }
         } else {
            return null;
         }
      }
   }

   private String[] beanNamesForStream(ResolvableType requiredType, boolean includeNonSingletons, boolean allowEagerInit) {
      return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, includeNonSingletons, allowEagerInit);
   }

   @Override
   public String[] getBeanNamesForType(ResolvableType type) {
      return this.getBeanNamesForType(type, true, true);
   }

   @Override
   public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
      Class<?> resolved = type.resolve();
      return resolved != null && !type.hasGenerics()
         ? this.getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit)
         : this.doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit);
   }

   @Override
   public String[] getBeanNamesForType(@Nullable Class<?> type) {
      return this.getBeanNamesForType(type, true, true);
   }

   @Override
   public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
      if (this.isConfigurationFrozen() && type != null && allowEagerInit) {
         Map<Class<?>, String[]> cache = includeNonSingletons ? this.allBeanNamesByType : this.singletonBeanNamesByType;
         String[] resolvedBeanNames = cache.get(type);
         if (resolvedBeanNames != null) {
            return resolvedBeanNames;
         }

         resolvedBeanNames = this.doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
         if (ClassUtils.isCacheSafe(type, this.getBeanClassLoader())) {
            cache.put(type, resolvedBeanNames);
         }

         return resolvedBeanNames;
      } else {
         return this.doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
      }
   }

   private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
      List<String> result = new ArrayList<>();

      for (String beanName : this.beanDefinitionNames) {
         if (!this.isAlias(beanName)) {
            try {
               RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
               if (!mbd.isAbstract()
                  && (
                     allowEagerInit
                        || (mbd.hasBeanClass() || !mbd.isLazyInit() || this.isAllowEagerClassLoading())
                           && !this.requiresEagerInitForType(mbd.getFactoryBeanName())
                  )) {
                  boolean isFactoryBean = this.isFactoryBean(beanName, mbd);
                  BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
                  boolean matchFound = false;
                  boolean allowFactoryBeanInit = allowEagerInit || this.containsSingleton(beanName);
                  boolean isNonLazyDecorated = dbd != null && !mbd.isLazyInit();
                  if (!isFactoryBean) {
                     if (includeNonSingletons || this.isSingleton(beanName, mbd, dbd)) {
                        matchFound = this.isTypeMatch(beanName, type, allowFactoryBeanInit);
                     }
                  } else {
                     if (!includeNonSingletons && !isNonLazyDecorated) {
                        if (allowFactoryBeanInit) {
                           matchFound = this.isTypeMatch(beanName, type, allowFactoryBeanInit) && this.isSingleton(beanName, mbd, dbd);
                        }
                     } else {
                        matchFound = this.isTypeMatch(beanName, type, allowFactoryBeanInit);
                     }

                     if (!matchFound) {
                        beanName = "&" + beanName;
                        if (includeNonSingletons || this.isSingleton(beanName, mbd, dbd)) {
                           matchFound = this.isTypeMatch(beanName, type, allowFactoryBeanInit);
                        }
                     }
                  }

                  if (matchFound) {
                     result.add(beanName);
                  }
               }
            } catch (CannotLoadBeanClassException | BeanDefinitionStoreException ex) {
               if (allowEagerInit) {
                  throw ex;
               }

               LogMessage message = ex instanceof CannotLoadBeanClassException
                  ? LogMessage.format("Ignoring bean class loading failure for bean '%s'", beanName)
                  : LogMessage.format("Ignoring unresolvable metadata in bean definition '%s'", beanName);
               this.logger.trace(message, ex);
               this.onSuppressedException(ex);
            } catch (NoSuchBeanDefinitionException var15) {
            }
         }
      }

      for (String beanName : this.manualSingletonNames) {
         try {
            if (this.isFactoryBean(beanName)) {
               if ((includeNonSingletons || this.isSingleton(beanName)) && this.isTypeMatch(beanName, type)) {
                  result.add(beanName);
                  continue;
               }

               beanName = "&" + beanName;
            }

            if (this.isTypeMatch(beanName, type)) {
               result.add(beanName);
            }
         } catch (NoSuchBeanDefinitionException ex) {
            this.logger.trace(LogMessage.format("Failed to check manually registered singleton with name '%s'", beanName), ex);
         }
      }

      return StringUtils.toStringArray(result);
   }

   private boolean isSingleton(String beanName, RootBeanDefinition mbd, @Nullable BeanDefinitionHolder dbd) {
      return dbd != null ? mbd.isSingleton() : this.isSingleton(beanName);
   }

   private boolean requiresEagerInitForType(@Nullable String factoryBeanName) {
      return factoryBeanName != null && this.isFactoryBean(factoryBeanName) && !this.containsSingleton(factoryBeanName);
   }

   @Override
   public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
      return this.getBeansOfType(type, true, true);
   }

   @Override
   public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
      String[] beanNames = this.getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
      Map<String, T> result = CollectionUtils.newLinkedHashMap(beanNames.length);

      for (String beanName : beanNames) {
         try {
            Object beanInstance = type != null ? this.getBean(beanName, type) : this.getBean(beanName);
            if (!(beanInstance instanceof NullBean)) {
               result.put(beanName, (T)beanInstance);
            }
         } catch (BeanNotOfRequiredTypeException var14) {
         } catch (BeanCreationException ex) {
            if (ex.getMostSpecificCause() instanceof BeanCurrentlyInCreationException bce) {
               String exBeanName = bce.getBeanName();
               if (exBeanName != null && this.isCurrentlyInCreation(exBeanName)) {
                  if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Ignoring match to currently created bean '" + exBeanName + "': " + ex.getMessage());
                  }

                  this.onSuppressedException(ex);
                  continue;
               }
            }

            throw ex;
         }
      }

      return result;
   }

   @Override
   public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
      List<String> result = new ArrayList<>();

      for (String beanName : this.beanDefinitionNames) {
         BeanDefinition bd = this.beanDefinitionMap.get(beanName);
         if (bd != null && !bd.isAbstract() && this.findAnnotationOnBean(beanName, annotationType) != null) {
            result.add(beanName);
         }
      }

      for (String beanName : this.manualSingletonNames) {
         if (!result.contains(beanName) && this.findAnnotationOnBean(beanName, annotationType) != null) {
            result.add(beanName);
         }
      }

      return StringUtils.toStringArray(result);
   }

   @Override
   public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
      String[] beanNames = this.getBeanNamesForAnnotation(annotationType);
      Map<String, Object> result = CollectionUtils.newLinkedHashMap(beanNames.length);

      for (String beanName : beanNames) {
         Object beanInstance = this.getBean(beanName);
         if (!(beanInstance instanceof NullBean)) {
            result.put(beanName, beanInstance);
         }
      }

      return result;
   }

   @Override
   public <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {
      return this.findAnnotationOnBean(beanName, annotationType, true);
   }

   @Override
   public <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      Class<?> beanType = this.getType(beanName, allowFactoryBeanInit);
      if (beanType != null) {
         MergedAnnotation<A> annotation = MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
         if (annotation.isPresent()) {
            return (A)annotation.synthesize();
         }
      }

      if (this.containsBeanDefinition(beanName)) {
         RootBeanDefinition bd = this.getMergedLocalBeanDefinition(beanName);
         if (bd.hasBeanClass() && bd.getFactoryMethodName() == null) {
            Class<?> beanClass = bd.getBeanClass();
            if (beanClass != beanType) {
               MergedAnnotation<A> annotation = MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
               if (annotation.isPresent()) {
                  return (A)annotation.synthesize();
               }
            }
         }

         Method factoryMethod = bd.getResolvedFactoryMethod();
         if (factoryMethod != null) {
            MergedAnnotation<A> annotation = MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
            if (annotation.isPresent()) {
               return (A)annotation.synthesize();
            }
         }
      }

      return null;
   }

   @Override
   public <A extends Annotation> Set<A> findAllAnnotationsOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      Set<A> annotations = new LinkedHashSet<>();
      Class<?> beanType = this.getType(beanName, allowFactoryBeanInit);
      if (beanType != null) {
         MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY)
            .stream(annotationType)
            .filter(MergedAnnotation::isPresent)
            .forEach(mergedAnnotation -> annotations.add((A)mergedAnnotation.synthesize()));
      }

      if (this.containsBeanDefinition(beanName)) {
         RootBeanDefinition bd = this.getMergedLocalBeanDefinition(beanName);
         if (bd.hasBeanClass() && bd.getFactoryMethodName() == null) {
            Class<?> beanClass = bd.getBeanClass();
            if (beanClass != beanType) {
               MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY)
                  .stream(annotationType)
                  .filter(MergedAnnotation::isPresent)
                  .forEach(mergedAnnotation -> annotations.add((A)mergedAnnotation.synthesize()));
            }
         }

         Method factoryMethod = bd.getResolvedFactoryMethod();
         if (factoryMethod != null) {
            MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY)
               .stream(annotationType)
               .filter(MergedAnnotation::isPresent)
               .forEach(mergedAnnotation -> annotations.add((A)mergedAnnotation.synthesize()));
         }
      }

      return annotations;
   }

   @Override
   public void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue) {
      Assert.notNull(dependencyType, "Dependency type must not be null");
      if (autowiredValue != null) {
         if (!(autowiredValue instanceof ObjectFactory) && !dependencyType.isInstance(autowiredValue)) {
            throw new IllegalArgumentException("Value [" + autowiredValue + "] does not implement specified dependency type [" + dependencyType.getName() + "]");
         }

         this.resolvableDependencies.put(dependencyType, autowiredValue);
      }
   }

   @Override
   public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor) throws NoSuchBeanDefinitionException {
      return this.isAutowireCandidate(beanName, descriptor, this.getAutowireCandidateResolver());
   }

   protected boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor, AutowireCandidateResolver resolver) throws NoSuchBeanDefinitionException {
      String bdName = this.transformedBeanName(beanName);
      if (this.containsBeanDefinition(bdName)) {
         return this.isAutowireCandidate(beanName, this.getMergedLocalBeanDefinition(bdName), descriptor, resolver);
      } else if (this.containsSingleton(beanName)) {
         return this.isAutowireCandidate(beanName, new RootBeanDefinition(this.getType(beanName)), descriptor, resolver);
      } else {
         BeanFactory parent = this.getParentBeanFactory();
         if (parent instanceof DefaultListableBeanFactory dlbf) {
            return dlbf.isAutowireCandidate(beanName, descriptor, resolver);
         } else {
            return parent instanceof ConfigurableListableBeanFactory clbf ? clbf.isAutowireCandidate(beanName, descriptor) : true;
         }
      }
   }

   protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd, DependencyDescriptor descriptor, AutowireCandidateResolver resolver) {
      String bdName = this.transformedBeanName(beanName);
      this.resolveBeanClass(mbd, bdName);
      if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
         new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
      }

      BeanDefinitionHolder holder = beanName.equals(bdName)
         ? this.mergedBeanDefinitionHolders.computeIfAbsent(beanName, key -> new BeanDefinitionHolder(mbd, beanName, this.getAliases(bdName)))
         : new BeanDefinitionHolder(mbd, beanName, this.getAliases(bdName));
      return resolver.isAutowireCandidate(holder, descriptor);
   }

   @Override
   public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
      BeanDefinition bd = this.beanDefinitionMap.get(beanName);
      if (bd == null) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No bean named '" + beanName + "' found in " + this);
         }

         throw new NoSuchBeanDefinitionException(beanName);
      } else {
         return bd;
      }
   }

   @Override
   public Iterator<String> getBeanNamesIterator() {
      CompositeIterator<String> iterator = new CompositeIterator();
      iterator.add(this.beanDefinitionNames.iterator());
      iterator.add(this.manualSingletonNames.iterator());
      return iterator;
   }

   @Override
   protected void clearMergedBeanDefinition(String beanName) {
      super.clearMergedBeanDefinition(beanName);
      this.mergedBeanDefinitionHolders.remove(beanName);
   }

   @Override
   public void clearMetadataCache() {
      super.clearMetadataCache();
      this.mergedBeanDefinitionHolders.clear();
      this.clearByTypeCache();
   }

   @Override
   public void freezeConfiguration() {
      this.clearMetadataCache();
      this.configurationFrozen = true;
      this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
   }

   @Override
   public boolean isConfigurationFrozen() {
      return this.configurationFrozen;
   }

   @Override
   protected boolean isBeanEligibleForMetadataCaching(String beanName) {
      return this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName);
   }

   @Override
   protected @Nullable Object obtainInstanceFromSupplier(Supplier<?> supplier, String beanName, RootBeanDefinition mbd) throws Exception {
      return supplier instanceof InstanceSupplier<?> instanceSupplier
         ? instanceSupplier.get(RegisteredBean.of(this, beanName, mbd))
         : super.obtainInstanceFromSupplier(supplier, beanName, mbd);
   }

   @Override
   protected void cacheMergedBeanDefinition(RootBeanDefinition mbd, String beanName) {
      super.cacheMergedBeanDefinition(mbd, beanName);
      if (mbd.isPrimary()) {
         this.primaryBeanNamesWithType.put(beanName, Void.class);
      }
   }

   @Override
   protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object @Nullable [] args) {
      super.checkMergedBeanDefinition(mbd, beanName, args);
      if (mbd.isBackgroundInit()) {
         if (this.preInstantiationThread.get() == DefaultListableBeanFactory.PreInstantiation.MAIN && this.getBootstrapExecutor() != null) {
            throw new BeanCurrentlyInCreationException(
               beanName,
               "Bean marked for background initialization but requested in mainline thread - declare ObjectProvider or lazy injection point in dependent mainline beans"
            );
         }
      } else if (this.preInstantiationThread.get() == DefaultListableBeanFactory.PreInstantiation.BACKGROUND) {
         throw new BeanCurrentlyInCreationException(
            beanName,
            "Bean marked for mainline initialization but requested in background thread - enforce early instantiation in mainline thread through depends-on '"
               + beanName
               + "' declaration for dependent background beans"
         );
      }
   }

   @Override
   protected @Nullable Boolean isCurrentThreadAllowedToHoldSingletonLock() {
      String mainThreadPrefix = this.mainThreadPrefix;
      if (mainThreadPrefix != null) {
         DefaultListableBeanFactory.PreInstantiation preInstantiation = (DefaultListableBeanFactory.PreInstantiation)this.preInstantiationThread.get();
         if (preInstantiation != null) {
            return switch (preInstantiation) {
               case MAIN -> Boolean.TRUE.equals(this.strictLocking) ? null : true;
               case BACKGROUND -> false;
            };
         }

         if (Boolean.FALSE.equals(this.strictLocking)) {
            return true;
         }

         if (this.strictLocking == null && !getThreadNamePrefix().equals(mainThreadPrefix)) {
            return true;
         }
      }

      return null;
   }

   @Override
   public void prepareSingletonBootstrap() {
      this.mainThreadPrefix = getThreadNamePrefix();
   }

   @Override
   public void preInstantiateSingletons() throws BeansException {
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Pre-instantiating singletons in " + this);
      }

      List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);
      this.preInstantiationThread.set(DefaultListableBeanFactory.PreInstantiation.MAIN);
      if (this.mainThreadPrefix == null) {
         this.mainThreadPrefix = getThreadNamePrefix();
      }

      try {
         List<CompletableFuture<?>> futures = new ArrayList<>();

         for (String beanName : beanNames) {
            RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
            if (!mbd.isAbstract() && mbd.isSingleton()) {
               CompletableFuture<?> future = this.preInstantiateSingleton(beanName, mbd);
               if (future != null) {
                  futures.add(future);
               }
            }
         }

         if (!futures.isEmpty()) {
            try {
               CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (CompletionException ex) {
               ReflectionUtils.rethrowRuntimeException(ex.getCause());
            }
         }
      } finally {
         this.mainThreadPrefix = null;
         this.preInstantiationThread.remove();
      }

      for (String beanName : beanNames) {
         if (this.getSingleton(beanName, false) instanceof SmartInitializingSingleton smartSingleton) {
            StartupStep smartInitialize = this.getApplicationStartup().start("spring.beans.smart-initialize").tag("beanName", beanName);
            smartSingleton.afterSingletonsInstantiated();
            smartInitialize.end();
         }
      }
   }

   private @Nullable CompletableFuture<?> preInstantiateSingleton(String beanName, RootBeanDefinition mbd) {
      if (mbd.isBackgroundInit()) {
         Executor executor = this.getBootstrapExecutor();
         if (executor != null) {
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
               for (String dep : dependsOn) {
                  this.getBean(dep);
               }
            }

            String factoryBeanName = mbd.getFactoryBeanName();
            if (factoryBeanName != null) {
               this.getBean(factoryBeanName);
            }

            CompletableFuture<?> future = CompletableFuture.runAsync(() -> this.instantiateSingletonInBackgroundThread(beanName), executor);
            this.addSingletonFactory(beanName, () -> {
               try {
                  future.join();
               } catch (CompletionException ex) {
                  ReflectionUtils.rethrowRuntimeException(exx.getCause());
               }

               return future;
            });
            return !mbd.isLazyInit() ? future : null;
         }

         if (this.logger.isInfoEnabled()) {
            this.logger
               .info(
                  "Bean '"
                     + beanName
                     + "' marked for background initialization without bootstrap executor configured - falling back to mainline initialization"
               );
         }
      }

      if (!mbd.isLazyInit()) {
         try {
            this.instantiateSingleton(beanName);
         } catch (BeanCurrentlyInCreationException ex) {
            this.logger
               .info(
                  "Bean '"
                     + beanName
                     + "' marked for pre-instantiation (not lazy-init) but currently initialized by other thread - skipping it in mainline thread"
               );
         }
      }

      return null;
   }

   private void instantiateSingletonInBackgroundThread(String beanName) {
      this.preInstantiationThread.set(DefaultListableBeanFactory.PreInstantiation.BACKGROUND);

      try {
         this.instantiateSingleton(beanName);
      } catch (RuntimeException | Error ex) {
         if (this.logger.isWarnEnabled()) {
            this.logger.warn("Failed to instantiate singleton bean '" + beanName + "' in background thread", ex);
         }

         throw ex;
      } finally {
         this.preInstantiationThread.remove();
      }
   }

   private void instantiateSingleton(String beanName) {
      if (this.isFactoryBean(beanName)) {
         if (this.getBean("&" + beanName) instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isEagerInit()) {
            this.getBean(beanName);
         }
      } else {
         this.getBean(beanName);
      }
   }

   private Object resolveBean(String beanName, ResolvableType requiredType) {
      try {
         return this.getBean(beanName, requiredType.toClass());
      } catch (BeanNotOfRequiredTypeException ex) {
         return this.getBean(beanName);
      }
   }

   private static String getThreadNamePrefix() {
      String name = Thread.currentThread().getName();
      int numberSeparator = name.lastIndexOf(45);
      return numberSeparator >= 0 ? name.substring(0, numberSeparator) : name;
   }

   @Override
   public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException {
      Assert.hasText(beanName, "Bean name must not be empty");
      Assert.notNull(beanDefinition, "BeanDefinition must not be null");
      if (beanDefinition instanceof AbstractBeanDefinition abd) {
         try {
            abd.validate();
         } catch (BeanDefinitionValidationException ex) {
            throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName, "Validation of bean definition failed", ex);
         }
      }

      BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
      if (existingDefinition != null) {
         if (!this.isBeanDefinitionOverridable(beanName)) {
            throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
         }

         this.logBeanDefinitionOverriding(beanName, beanDefinition, existingDefinition);
         this.beanDefinitionMap.put(beanName, beanDefinition);
      } else {
         if (this.isAlias(beanName)) {
            String aliasedName = this.canonicalName(beanName);
            if (!this.isBeanDefinitionOverridable(aliasedName)) {
               if (this.containsBeanDefinition(aliasedName)) {
                  throw new BeanDefinitionOverrideException(beanName, beanDefinition, this.getBeanDefinition(aliasedName));
               }

               throw new BeanDefinitionStoreException(
                  beanDefinition.getResourceDescription(),
                  beanName,
                  "Cannot register bean definition for bean '" + beanName + "' since there is already an alias for bean '" + aliasedName + "' bound."
               );
            }

            if (this.logger.isInfoEnabled()) {
               this.logger
                  .info(
                     "Removing alias '"
                        + beanName
                        + "' for bean '"
                        + aliasedName
                        + "' due to registration of bean definition for bean '"
                        + beanName
                        + "': ["
                        + beanDefinition
                        + "]"
                  );
            }

            this.removeAlias(beanName);
         }

         if (this.hasBeanCreationStarted()) {
            synchronized (this.beanDefinitionMap) {
               this.beanDefinitionMap.put(beanName, beanDefinition);
               List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
               updatedDefinitions.addAll(this.beanDefinitionNames);
               updatedDefinitions.add(beanName);
               this.beanDefinitionNames = updatedDefinitions;
               this.removeManualSingletonName(beanName);
            }
         } else {
            this.beanDefinitionMap.put(beanName, beanDefinition);
            this.beanDefinitionNames.add(beanName);
            this.removeManualSingletonName(beanName);
         }

         this.frozenBeanDefinitionNames = null;
      }

      if (existingDefinition != null || this.containsSingleton(beanName)) {
         this.resetBeanDefinition(beanName);
      } else if (this.isConfigurationFrozen()) {
         this.clearByTypeCache();
      }

      if (beanDefinition.isPrimary()) {
         this.primaryBeanNamesWithType.put(beanName, Void.class);
      }
   }

   private void logBeanDefinitionOverriding(String beanName, BeanDefinition beanDefinition, BeanDefinition existingDefinition) {
      boolean explicitBeanOverride = this.allowBeanDefinitionOverriding != null;
      if (existingDefinition.getRole() < beanDefinition.getRole()) {
         if (this.logger.isInfoEnabled()) {
            this.logger
               .info(
                  "Overriding user-defined bean definition for bean '"
                     + beanName
                     + "' with a framework-generated bean definition: replacing ["
                     + existingDefinition
                     + "] with ["
                     + beanDefinition
                     + "]"
               );
         }
      } else if (!beanDefinition.equals(existingDefinition)) {
         if (explicitBeanOverride && this.logger.isInfoEnabled()) {
            this.logger
               .info(
                  "Overriding bean definition for bean '"
                     + beanName
                     + "' with a different definition: replacing ["
                     + existingDefinition
                     + "] with ["
                     + beanDefinition
                     + "]"
               );
         }

         if (this.logger.isDebugEnabled()) {
            this.logger
               .debug(
                  "Overriding bean definition for bean '"
                     + beanName
                     + "' with a different definition: replacing ["
                     + existingDefinition
                     + "] with ["
                     + beanDefinition
                     + "]"
               );
         }
      } else {
         if (explicitBeanOverride && this.logger.isInfoEnabled()) {
            this.logger
               .info(
                  "Overriding bean definition for bean '"
                     + beanName
                     + "' with an equivalent definition: replacing ["
                     + existingDefinition
                     + "] with ["
                     + beanDefinition
                     + "]"
               );
         }

         if (this.logger.isTraceEnabled()) {
            this.logger
               .trace(
                  "Overriding bean definition for bean '"
                     + beanName
                     + "' with an equivalent definition: replacing ["
                     + existingDefinition
                     + "] with ["
                     + beanDefinition
                     + "]"
               );
         }
      }
   }

   @Override
   public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
      Assert.hasText(beanName, "'beanName' must not be empty");
      BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
      if (bd == null) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No bean named '" + beanName + "' found in " + this);
         }

         throw new NoSuchBeanDefinitionException(beanName);
      } else {
         if (this.hasBeanCreationStarted()) {
            synchronized (this.beanDefinitionMap) {
               List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
               updatedDefinitions.remove(beanName);
               this.beanDefinitionNames = updatedDefinitions;
            }
         } else {
            this.beanDefinitionNames.remove(beanName);
         }

         this.frozenBeanDefinitionNames = null;
         this.resetBeanDefinition(beanName);
      }
   }

   protected void resetBeanDefinition(String beanName) {
      this.clearMergedBeanDefinition(beanName);
      this.destroySingleton(beanName);
      this.primaryBeanNamesWithType.remove(beanName);

      for (MergedBeanDefinitionPostProcessor processor : this.getBeanPostProcessorCache().mergedDefinition) {
         processor.resetBeanDefinition(beanName);
      }

      for (String bdName : this.beanDefinitionNames) {
         if (!beanName.equals(bdName)) {
            BeanDefinition bd = this.beanDefinitionMap.get(bdName);
            if (bd != null && beanName.equals(bd.getParentName())) {
               this.resetBeanDefinition(bdName);
            }
         }
      }
   }

   @Override
   public boolean isBeanDefinitionOverridable(String beanName) {
      return this.isAllowBeanDefinitionOverriding();
   }

   protected boolean allowAliasOverriding() {
      return this.isAllowBeanDefinitionOverriding();
   }

   protected void checkForAliasCircle(String name, String alias) {
      super.checkForAliasCircle(name, alias);
      if (!this.isBeanDefinitionOverridable(alias) && this.containsBeanDefinition(alias)) {
         throw new IllegalStateException("Cannot register alias '" + alias + "' for name '" + name + "': Alias would override bean definition '" + alias + "'");
      }
   }

   @Override
   protected void addSingleton(String beanName, Object singletonObject) {
      super.addSingleton(beanName, singletonObject);
      Predicate<Class<?>> filter = beanTypex -> beanTypex != Object.class && beanTypex.isInstance(singletonObject);
      this.allBeanNamesByType.keySet().removeIf(filter);
      this.singletonBeanNamesByType.keySet().removeIf(filter);
      if (this.primaryBeanNamesWithType.containsKey(beanName) && singletonObject.getClass() != NullBean.class) {
         Class<?> beanType = singletonObject instanceof FactoryBean<?> fb ? this.getTypeForFactoryBean(fb) : singletonObject.getClass();
         if (beanType != null) {
            this.primaryBeanNamesWithType.put(beanName, beanType);
         }
      }
   }

   @Override
   public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
      super.registerSingleton(beanName, singletonObject);
      this.updateManualSingletonNames(set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName));
      this.allBeanNamesByType.remove(Object.class);
      this.singletonBeanNamesByType.remove(Object.class);
   }

   @Override
   public void destroySingletons() {
      super.destroySingletons();
      this.updateManualSingletonNames(Set::clear, set -> !set.isEmpty());
      this.clearByTypeCache();
   }

   @Override
   public void destroySingleton(String beanName) {
      super.destroySingleton(beanName);
      this.removeManualSingletonName(beanName);
      this.clearByTypeCache();
   }

   private void removeManualSingletonName(String beanName) {
      this.updateManualSingletonNames(set -> set.remove(beanName), set -> set.contains(beanName));
   }

   private void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition) {
      if (this.hasBeanCreationStarted()) {
         synchronized (this.beanDefinitionMap) {
            if (condition.test(this.manualSingletonNames)) {
               Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
               action.accept(updatedSingletons);
               this.manualSingletonNames = updatedSingletons;
            }
         }
      } else if (condition.test(this.manualSingletonNames)) {
         action.accept(this.manualSingletonNames);
      }
   }

   private void clearByTypeCache() {
      this.allBeanNamesByType.clear();
      this.singletonBeanNamesByType.clear();
   }

   @Override
   public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
      Assert.notNull(requiredType, "Required type must not be null");
      NamedBeanHolder<T> namedBean = this.resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);
      if (namedBean != null) {
         return namedBean;
      } else if (this.getParentBeanFactory() instanceof AutowireCapableBeanFactory acbf) {
         return acbf.resolveNamedBean(requiredType);
      } else {
         throw new NoSuchBeanDefinitionException(requiredType);
      }
   }

   private <T> @Nullable NamedBeanHolder<T> resolveNamedBean(ResolvableType requiredType, @Nullable Object @Nullable [] args, boolean nonUniqueAsNull) throws BeansException {
      Assert.notNull(requiredType, "Required type must not be null");
      String[] candidateNames = this.getBeanNamesForType(requiredType);
      if (candidateNames.length > 1) {
         List<String> autowireCandidates = new ArrayList<>(candidateNames.length);

         for (String beanName : candidateNames) {
            if (!this.containsBeanDefinition(beanName) || this.getBeanDefinition(beanName).isAutowireCandidate()) {
               autowireCandidates.add(beanName);
            }
         }

         if (!autowireCandidates.isEmpty()) {
            candidateNames = StringUtils.toStringArray(autowireCandidates);
         }
      }

      if (candidateNames.length == 1) {
         return this.resolveNamedBean(candidateNames[0], requiredType, args);
      }

      if (candidateNames.length > 1) {
         Map<String, Object> candidates = CollectionUtils.newLinkedHashMap(candidateNames.length);

         for (String beanName : candidateNames) {
            if (this.containsSingleton(beanName) && args == null) {
               Object beanInstance = this.resolveBean(beanName, requiredType);
               candidates.put(beanName, beanInstance instanceof NullBean ? null : beanInstance);
            } else {
               candidates.put(beanName, this.getType(beanName));
            }
         }

         String candidateName = this.determinePrimaryCandidate(candidates, requiredType.toClass());
         if (candidateName == null) {
            candidateName = this.determineHighestPriorityCandidate(candidates, requiredType.toClass());
         }

         if (candidateName == null) {
            candidateName = this.determineDefaultCandidate(candidates);
         }

         if (candidateName != null) {
            Object beanInstance = candidates.get(candidateName);
            if (beanInstance == null) {
               return null;
            }

            if (beanInstance instanceof Class) {
               return this.resolveNamedBean(candidateName, requiredType, args);
            }

            return new NamedBeanHolder<>(candidateName, (T)beanInstance);
         }

         if (!nonUniqueAsNull) {
            throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
         }
      }

      return null;
   }

   private <T> @Nullable NamedBeanHolder<T> resolveNamedBean(String beanName, ResolvableType requiredType, @Nullable Object @Nullable [] args) throws BeansException {
      Object bean = args != null ? this.getBean(beanName, args) : this.resolveBean(beanName, requiredType);
      return bean instanceof NullBean ? null : new NamedBeanHolder<>(beanName, this.adaptBeanInstance(beanName, bean, requiredType.toClass()));
   }

   @Override
   public @Nullable Object resolveDependency(
      DependencyDescriptor descriptor, @Nullable String requestingBeanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter
   ) throws BeansException {
      descriptor.initParameterNameDiscovery(this.getParameterNameDiscoverer());
      if (Optional.class == descriptor.getDependencyType()) {
         return this.createOptionalDependency(descriptor, requestingBeanName, autowiredBeanNames, null);
      }

      if (ObjectFactory.class != descriptor.getDependencyType() && ObjectProvider.class != descriptor.getDependencyType()) {
         if (jakartaInjectProviderClass == descriptor.getDependencyType()) {
            return new DefaultListableBeanFactory.Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
         }

         if (descriptor.supportsLazyResolution()) {
            Object result = this.getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(descriptor, requestingBeanName);
            if (result != null) {
               return result;
            }
         }

         return this.doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
      } else {
         return new DefaultListableBeanFactory.DependencyObjectProvider(descriptor, requestingBeanName);
      }
   }

   public @Nullable Object doResolveDependency(
      DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter
   ) throws BeansException {
      InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);

      try {
         Object shortcut = descriptor.resolveShortcut(this);
         if (shortcut != null) {
            return shortcut;
         }

         Class<?> type = descriptor.getDependencyType();
         Object value = this.getAutowireCandidateResolver().getSuggestedValue(descriptor);
         if (value != null) {
            if (value instanceof String strValue) {
               String resolvedValue = this.resolveEmbeddedValue(strValue);
               BeanDefinition bd = beanName != null && this.containsBean(beanName) ? this.getMergedBeanDefinition(beanName) : null;
               value = this.evaluateBeanDefinitionString(resolvedValue, bd);
            }

            TypeConverter converter = typeConverter != null ? typeConverter : this.getTypeConverter();

            try {
               return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
            } catch (UnsupportedOperationException ex) {
               return descriptor.getField() != null
                  ? converter.convertIfNecessary(value, type, descriptor.getField())
                  : converter.convertIfNecessary(value, type, descriptor.getMethodParameter());
            }
         } else {
            if (descriptor.usesStandardBeanLookup()) {
               String dependencyName = descriptor.getDependencyName();
               if (dependencyName == null || !this.containsBean(dependencyName)) {
                  String suggestedName = this.getAutowireCandidateResolver().getSuggestedName(descriptor);
                  dependencyName = suggestedName != null && this.containsBean(suggestedName) ? suggestedName : null;
               }

               if (dependencyName != null) {
                  dependencyName = this.canonicalName(dependencyName);
                  if (this.isTypeMatch(dependencyName, type)
                     && this.isAutowireCandidate(dependencyName, descriptor)
                     && !this.isFallback(dependencyName)
                     && !this.hasPrimaryConflict(dependencyName, type)
                     && !this.isSelfReference(beanName, dependencyName)) {
                     if (autowiredBeanNames != null) {
                        autowiredBeanNames.add(dependencyName);
                     }

                     Object dependencyBean = this.resolveBean(dependencyName, descriptor.getResolvableType());
                     return this.resolveInstance(dependencyBean, descriptor, type, dependencyName);
                  }
               }
            }

            Object multipleBeans = this.resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
            if (multipleBeans != null) {
               return multipleBeans;
            }

            Map<String, Object> matchingBeans = this.findAutowireCandidates(beanName, type, descriptor);
            if (matchingBeans.isEmpty()) {
               multipleBeans = this.resolveMultipleBeansFallback(descriptor, beanName, autowiredBeanNames, typeConverter);
               if (multipleBeans != null) {
                  return multipleBeans;
               }

               if (this.isRequired(descriptor)) {
                  this.raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
               }

               return null;
            } else {
               String autowiredBeanName;
               Object instanceCandidate;
               if (matchingBeans.size() > 1) {
                  autowiredBeanName = this.determineAutowireCandidate(matchingBeans, descriptor);
                  if (autowiredBeanName == null) {
                     if (!this.isRequired(descriptor) && this.indicatesArrayCollectionOrMap(type)) {
                        return null;
                     }

                     return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
                  }

                  instanceCandidate = matchingBeans.get(autowiredBeanName);
               } else {
                  Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
                  autowiredBeanName = entry.getKey();
                  instanceCandidate = entry.getValue();
               }

               if (autowiredBeanNames != null) {
                  autowiredBeanNames.add(autowiredBeanName);
               }

               if (instanceCandidate instanceof Class) {
                  instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
               }

               return this.resolveInstance(instanceCandidate, descriptor, type, autowiredBeanName);
            }
         }
      } finally {
         ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
      }
   }

   private @Nullable Object resolveInstance(Object candidate, DependencyDescriptor descriptor, Class<?> type, String name) {
      Object result = candidate;
      if (result instanceof NullBean) {
         if (this.isRequired(descriptor)) {
            this.raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
         }

         result = null;
      }

      if (!ClassUtils.isAssignableValue(type, result)) {
         throw new BeanNotOfRequiredTypeException(name, type, candidate.getClass());
      } else {
         return result;
      }
   }

   private @Nullable Object resolveMultipleBeans(
      DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter
   ) {
      Class<?> type = descriptor.getDependencyType();
      if (descriptor instanceof DefaultListableBeanFactory.StreamDependencyDescriptor streamDependencyDescriptor) {
         Map<String, Object> matchingBeans = this.findAutowireCandidates(beanName, type, descriptor);
         if (autowiredBeanNames != null) {
            autowiredBeanNames.addAll(matchingBeans.keySet());
         }

         Stream<Object> stream = matchingBeans.keySet()
            .stream()
            .map(name -> descriptor.resolveCandidate(name, type, this))
            .filter(bean -> !(bean instanceof NullBean));
         if (streamDependencyDescriptor.isOrdered()) {
            stream = stream.sorted(this.adaptOrderComparator(matchingBeans));
         }

         return stream;
      } else if (type.isArray()) {
         Class<?> componentType = type.componentType();
         ResolvableType resolvableType = descriptor.getResolvableType();
         Class<?> resolvedArrayType = resolvableType.resolve(type);
         if (resolvedArrayType != type) {
            componentType = resolvableType.getComponentType().resolve();
         }

         if (componentType == null) {
            return null;
         }

         Map<String, Object> matchingBeans = this.findAutowireCandidates(
            beanName, componentType, new DefaultListableBeanFactory.MultiElementDescriptor(descriptor)
         );
         if (matchingBeans.isEmpty()) {
            return null;
         }

         if (autowiredBeanNames != null) {
            autowiredBeanNames.addAll(matchingBeans.keySet());
         }

         TypeConverter converter = typeConverter != null ? typeConverter : this.getTypeConverter();
         Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
         if (result instanceof Object[] array && array.length > 1) {
            Comparator<Object> comparator = this.adaptDependencyComparator(matchingBeans);
            if (comparator != null) {
               Arrays.sort(array, comparator);
            }
         }

         return result;
      } else if (Collection.class == type || Set.class == type || List.class == type) {
         return this.resolveMultipleBeanCollection(descriptor, beanName, autowiredBeanNames, typeConverter);
      } else {
         return Map.class == type ? this.resolveMultipleBeanMap(descriptor, beanName, autowiredBeanNames, typeConverter) : null;
      }
   }

   private @Nullable Object resolveMultipleBeansFallback(
      DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter
   ) {
      Class<?> type = descriptor.getDependencyType();
      if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
         return this.resolveMultipleBeanCollection(descriptor, beanName, autowiredBeanNames, typeConverter);
      } else {
         return Map.class.isAssignableFrom(type) && type.isInterface()
            ? this.resolveMultipleBeanMap(descriptor, beanName, autowiredBeanNames, typeConverter)
            : null;
      }
   }

   private @Nullable Object resolveMultipleBeanCollection(
      DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter
   ) {
      Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric(new int[0]);
      if (elementType == null) {
         return null;
      }

      Map<String, Object> matchingBeans = this.findAutowireCandidates(beanName, elementType, new DefaultListableBeanFactory.MultiElementDescriptor(descriptor));
      if (matchingBeans.isEmpty()) {
         return null;
      }

      if (autowiredBeanNames != null) {
         autowiredBeanNames.addAll(matchingBeans.keySet());
      }

      TypeConverter converter = typeConverter != null ? typeConverter : this.getTypeConverter();
      Object result = converter.convertIfNecessary(matchingBeans.values(), descriptor.getDependencyType());
      if (result instanceof List<?> list && list.size() > 1) {
         Comparator<Object> comparator = this.adaptDependencyComparator(matchingBeans);
         if (comparator != null) {
            list.sort(comparator);
         }
      }

      return result;
   }

   private @Nullable Object resolveMultipleBeanMap(
      DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter
   ) {
      ResolvableType mapType = descriptor.getResolvableType().asMap();
      Class<?> keyType = mapType.resolveGeneric(new int[]{0});
      if (String.class != keyType) {
         return null;
      }

      Class<?> valueType = mapType.resolveGeneric(new int[]{1});
      if (valueType == null) {
         return null;
      }

      Map<String, Object> matchingBeans = this.findAutowireCandidates(beanName, valueType, new DefaultListableBeanFactory.MultiElementDescriptor(descriptor));
      if (matchingBeans.isEmpty()) {
         return null;
      }

      if (autowiredBeanNames != null) {
         autowiredBeanNames.addAll(matchingBeans.keySet());
      }

      TypeConverter converter = typeConverter != null ? typeConverter : this.getTypeConverter();
      return converter.convertIfNecessary(matchingBeans, descriptor.getDependencyType());
   }

   private boolean indicatesArrayCollectionOrMap(Class<?> type) {
      return type.isArray() || type.isInterface() && (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type));
   }

   private boolean isRequired(DependencyDescriptor descriptor) {
      return this.getAutowireCandidateResolver().isRequired(descriptor);
   }

   private @Nullable Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {
      Comparator<Object> comparator = this.getDependencyComparator();
      return comparator instanceof OrderComparator orderComparator
         ? orderComparator.withSourceProvider(this.createFactoryAwareOrderSourceProvider(matchingBeans))
         : comparator;
   }

   private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {
      OrderComparator comparator = this.getDependencyComparator() instanceof OrderComparator orderComparator ? orderComparator : OrderComparator.INSTANCE;
      return comparator.withSourceProvider(this.createFactoryAwareOrderSourceProvider(matchingBeans));
   }

   private OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {
      IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();
      beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));
      return new DefaultListableBeanFactory.FactoryAwareOrderSourceProvider(instancesToBeanNames);
   }

   protected Map<String, Object> findAutowireCandidates(@Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {
      String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true, descriptor.isEager());
      Map<String, Object> result = CollectionUtils.newLinkedHashMap(candidateNames.length);

      for (Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
         Class<?> autowiringType = classObjectEntry.getKey();
         if (autowiringType.isAssignableFrom(requiredType)) {
            Object autowiringValue = classObjectEntry.getValue();
            autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
            if (requiredType.isInstance(autowiringValue)) {
               result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
               break;
            }
         }
      }

      for (String candidate : candidateNames) {
         if (!this.isSelfReference(beanName, candidate) && this.isAutowireCandidate(candidate, descriptor)) {
            this.addCandidateEntry(result, candidate, descriptor, requiredType);
         }
      }

      if (result.isEmpty()) {
         boolean multiple = this.indicatesArrayCollectionOrMap(requiredType);
         DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();

         for (String candidate : candidateNames) {
            if (!this.isSelfReference(beanName, candidate)
               && this.isAutowireCandidate(candidate, fallbackDescriptor)
               && (!multiple || this.matchesBeanName(candidate, descriptor.getDependencyName()) || this.getAutowireCandidateResolver().hasQualifier(descriptor))
               )
             {
               this.addCandidateEntry(result, candidate, descriptor, requiredType);
            }
         }

         if (result.isEmpty() && !multiple) {
            for (String candidate : candidateNames) {
               if (this.isSelfReference(beanName, candidate)
                  && (!(descriptor instanceof DefaultListableBeanFactory.MultiElementDescriptor) || !beanName.equals(candidate))
                  && this.isAutowireCandidate(candidate, fallbackDescriptor)) {
                  this.addCandidateEntry(result, candidate, descriptor, requiredType);
               }
            }
         }
      }

      return result;
   }

   private void addCandidateEntry(Map<String, Object> candidates, String candidateName, DependencyDescriptor descriptor, Class<?> requiredType) {
      if (descriptor instanceof DefaultListableBeanFactory.MultiElementDescriptor) {
         Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
         if (!(beanInstance instanceof NullBean)) {
            candidates.put(candidateName, beanInstance);
         }
      } else if (!this.containsSingleton(candidateName)
         && !(descriptor instanceof DefaultListableBeanFactory.StreamDependencyDescriptor streamDescriptor && streamDescriptor.isOrdered())) {
         candidates.put(candidateName, this.getType(candidateName));
      } else {
         Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
         candidates.put(candidateName, beanInstance);
      }
   }

   protected @Nullable String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
      Class<?> requiredType = descriptor.getDependencyType();
      String primaryCandidate = this.determinePrimaryCandidate(candidates, requiredType);
      if (primaryCandidate != null) {
         return primaryCandidate;
      }

      String dependencyName = descriptor.getDependencyName();
      if (dependencyName != null) {
         for (String beanName : candidates.keySet()) {
            if (this.matchesBeanName(beanName, dependencyName)) {
               return beanName;
            }
         }
      }

      String suggestedName = this.getAutowireCandidateResolver().getSuggestedName(descriptor);
      if (suggestedName != null) {
         for (String beanName : candidates.keySet()) {
            if (this.matchesBeanName(beanName, suggestedName)) {
               return beanName;
            }
         }
      }

      String priorityCandidate = this.determineHighestPriorityCandidate(candidates, requiredType);
      if (priorityCandidate != null) {
         return priorityCandidate;
      }

      String defaultCandidate = this.determineDefaultCandidate(candidates);
      if (defaultCandidate != null) {
         return defaultCandidate;
      }

      for (Entry<String, Object> entry : candidates.entrySet()) {
         String candidateName = entry.getKey();
         Object beanInstance = entry.getValue();
         if (beanInstance != null && this.resolvableDependencies.containsValue(beanInstance)) {
            return candidateName;
         }
      }

      return null;
   }

   protected @Nullable String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
      String primaryBeanName = null;

      for (Entry<String, Object> entry : candidates.entrySet()) {
         String candidateBeanName = entry.getKey();
         Object beanInstance = entry.getValue();
         if (this.isPrimary(candidateBeanName, beanInstance)) {
            if (primaryBeanName != null) {
               boolean candidateLocal = this.containsBeanDefinition(candidateBeanName);
               boolean primaryLocal = this.containsBeanDefinition(primaryBeanName);
               if (candidateLocal == primaryLocal) {
                  String message = "more than one 'primary' bean found among candidates: " + candidates.keySet();
                  this.logger.trace(message);
                  throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(), message);
               }

               if (candidateLocal) {
                  primaryBeanName = candidateBeanName;
               }
            } else {
               primaryBeanName = candidateBeanName;
            }
         }
      }

      if (primaryBeanName == null) {
         for (String candidateBeanName : candidates.keySet()) {
            if (!this.isFallback(candidateBeanName)) {
               if (primaryBeanName != null) {
                  return null;
               }

               primaryBeanName = candidateBeanName;
            }
         }
      }

      return primaryBeanName;
   }

   protected @Nullable String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
      String highestPriorityBeanName = null;
      Integer highestPriority = null;
      boolean highestPriorityConflictDetected = false;

      for (Entry<String, Object> entry : candidates.entrySet()) {
         String candidateBeanName = entry.getKey();
         Object beanInstance = entry.getValue();
         if (beanInstance != null) {
            Integer candidatePriority = this.getPriority(beanInstance);
            if (candidatePriority != null) {
               if (highestPriority != null) {
                  if (candidatePriority.equals(highestPriority)) {
                     highestPriorityConflictDetected = true;
                  } else if (candidatePriority < highestPriority) {
                     highestPriorityBeanName = candidateBeanName;
                     highestPriority = candidatePriority;
                     highestPriorityConflictDetected = false;
                  }
               } else {
                  highestPriorityBeanName = candidateBeanName;
                  highestPriority = candidatePriority;
               }
            }
         }
      }

      if (highestPriorityConflictDetected) {
         throw new NoUniqueBeanDefinitionException(
            requiredType,
            candidates.size(),
            "Multiple beans found with the same highest priority (" + highestPriority + ") among candidates: " + candidates.keySet()
         );
      } else {
         return highestPriorityBeanName;
      }
   }

   protected boolean isPrimary(String beanName, Object beanInstance) {
      String transformedBeanName = this.transformedBeanName(beanName);
      return this.containsBeanDefinition(transformedBeanName)
         ? this.getMergedLocalBeanDefinition(transformedBeanName).isPrimary()
         : this.getParentBeanFactory() instanceof DefaultListableBeanFactory parent && parent.isPrimary(transformedBeanName, beanInstance);
   }

   private boolean isFallback(String beanName) {
      String transformedBeanName = this.transformedBeanName(beanName);
      return this.containsBeanDefinition(transformedBeanName)
         ? this.getMergedLocalBeanDefinition(transformedBeanName).isFallback()
         : this.getParentBeanFactory() instanceof DefaultListableBeanFactory parent && parent.isFallback(transformedBeanName);
   }

   protected @Nullable Integer getPriority(Object beanInstance) {
      return this.getDependencyComparator() instanceof OrderComparator orderComparator ? orderComparator.getPriority(beanInstance) : null;
   }

   private @Nullable String determineDefaultCandidate(Map<String, Object> candidates) {
      String defaultBeanName = null;

      for (String candidateBeanName : candidates.keySet()) {
         if (AutowireUtils.isDefaultCandidate(this, candidateBeanName)) {
            if (defaultBeanName != null) {
               return null;
            }

            defaultBeanName = candidateBeanName;
         }
      }

      return defaultBeanName;
   }

   protected boolean matchesBeanName(String beanName, @Nullable String dependencyName) {
      return dependencyName != null && (dependencyName.equals(beanName) || ObjectUtils.containsElement(this.getAliases(beanName), dependencyName));
   }

   @Contract("null, _ -> false; _, null -> false;")
   private boolean isSelfReference(@Nullable String beanName, @Nullable String candidateName) {
      return beanName != null
         && candidateName != null
         && (
            beanName.equals(candidateName)
               || this.containsBeanDefinition(candidateName) && beanName.equals(this.getMergedLocalBeanDefinition(candidateName).getFactoryBeanName())
         );
   }

   private boolean hasPrimaryConflict(String beanName, Class<?> dependencyType) {
      for (Entry<String, Class<?>> candidate : this.primaryBeanNamesWithType.entrySet()) {
         String candidateName = candidate.getKey();
         Class<?> candidateType = candidate.getValue();
         if (!candidateName.equals(beanName)
            && (candidateType != Void.class ? dependencyType.isAssignableFrom(candidateType) : this.isTypeMatch(candidateName, dependencyType))) {
            return true;
         }
      }

      return this.getParentBeanFactory() instanceof DefaultListableBeanFactory parent && parent.hasPrimaryConflict(beanName, dependencyType);
   }

   private void raiseNoMatchingBeanFound(Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor) throws BeansException {
      this.checkBeanNotOfRequiredType(type, descriptor);
      throw new NoSuchBeanDefinitionException(
         resolvableType,
         "expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: " + ObjectUtils.nullSafeToString(descriptor.getAnnotations())
      );
   }

   private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
      for (String beanName : this.beanDefinitionNames) {
         try {
            RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
            Class<?> targetType = mbd.getTargetType();
            if (targetType != null
               && type.isAssignableFrom(targetType)
               && this.isAutowireCandidate(beanName, mbd, descriptor, this.getAutowireCandidateResolver())) {
               Object beanInstance = this.getSingleton(beanName, false);
               Class<?> beanType = beanInstance != null && beanInstance.getClass() != NullBean.class
                  ? beanInstance.getClass()
                  : this.predictBeanType(beanName, mbd);
               if (beanType != null && !type.isAssignableFrom(beanType)) {
                  throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
               }
            }
         } catch (NoSuchBeanDefinitionException var9) {
         }
      }

      if (this.getParentBeanFactory() instanceof DefaultListableBeanFactory parent) {
         parent.checkBeanNotOfRequiredType(type, descriptor);
      }
   }

   private Optional<?> createOptionalDependency(
      DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable Object @Nullable [] args
   ) {
      DependencyDescriptor descriptorToUse = new DefaultListableBeanFactory.NestedDependencyDescriptor(descriptor) {
         @Override
         public boolean isRequired() {
            return false;
         }

         @Override
         public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
            return !ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args) : super.resolveCandidate(beanName, requiredType, beanFactory);
         }

         @Override
         public boolean usesStandardBeanLookup() {
            return ObjectUtils.isEmpty(args);
         }
      };
      Object result = this.doResolveDependency(descriptorToUse, beanName, autowiredBeanNames, null);
      return result instanceof Optional<?> optional ? optional : Optional.ofNullable(result);
   }

   public int getOrder(String beanName) {
      return this.getOrder(beanName, this.getBean(beanName));
   }

   public int getOrder(String beanName, Object beanInstance) {
      OrderComparator comparator = this.getDependencyComparator() instanceof OrderComparator orderComparator ? orderComparator : OrderComparator.INSTANCE;
      return comparator.getOrder(beanInstance, new DefaultListableBeanFactory.FactoryAwareOrderSourceProvider(Collections.singletonMap(beanInstance, beanName)));
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
      sb.append(": defining beans [");
      sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
      sb.append("]; ");
      BeanFactory parent = this.getParentBeanFactory();
      if (parent == null) {
         sb.append("root of factory hierarchy");
      } else {
         sb.append("parent: ").append(ObjectUtils.identityToString(parent));
      }

      return sb.toString();
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - just a SerializedBeanFactoryReference is");
   }

   protected Object writeReplace() throws ObjectStreamException {
      if (this.serializationId != null) {
         return new DefaultListableBeanFactory.SerializedBeanFactoryReference(this.serializationId);
      } else {
         throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
      }
   }

   static {
      try {
         jakartaInjectProviderClass = ClassUtils.forName("jakarta.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
      } catch (ClassNotFoundException ex) {
         jakartaInjectProviderClass = null;
      }

      serializableFactories = new ConcurrentHashMap<>(8);
   }

   private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
   }

   private class DependencyObjectProvider implements DefaultListableBeanFactory.BeanObjectProvider<Object> {
      private static final Object NOT_CACHEABLE = new Object();
      private static final Object NULL_VALUE = new Object();
      private final DependencyDescriptor descriptor;
      private final boolean optional;
      private final @Nullable String beanName;
      private transient volatile @Nullable Object cachedValue;

      public DependencyObjectProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
         this.descriptor = new DefaultListableBeanFactory.NestedDependencyDescriptor(descriptor);
         this.optional = this.descriptor.getDependencyType() == Optional.class;
         this.beanName = beanName;
      }

      @Override
      public Object getObject() throws BeansException {
         Object result = this.getValue();
         if (result == null) {
            throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
         } else {
            return result;
         }
      }

      @Override
      public Object getObject(final @Nullable Object... args) throws BeansException {
         if (this.optional) {
            return DefaultListableBeanFactory.this.createOptionalDependency(this.descriptor, this.beanName, null, args);
         } else {
            DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
               @Override
               public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                  return beanFactory.getBean(beanName, args);
               }
            };
            Object result = DefaultListableBeanFactory.this.doResolveDependency(descriptorToUse, this.beanName, null, null);
            if (result == null) {
               throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
            } else {
               return result;
            }
         }
      }

      @Override
      public @Nullable Object getIfAvailable() throws BeansException {
         try {
            if (this.optional) {
               return DefaultListableBeanFactory.this.createOptionalDependency(this.descriptor, this.beanName, null, null);
            }

            DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
               @Override
               public boolean isRequired() {
                  return false;
               }

               @Override
               public boolean usesStandardBeanLookup() {
                  return true;
               }
            };
            return DefaultListableBeanFactory.this.doResolveDependency(descriptorToUse, this.beanName, null, null);
         } catch (ScopeNotActiveException ex) {
            return null;
         }
      }

      @Override
      public void ifAvailable(Consumer<Object> dependencyConsumer) throws BeansException {
         Object dependency = this.getIfAvailable();
         if (dependency != null) {
            try {
               dependencyConsumer.accept(dependency);
            } catch (ScopeNotActiveException var4) {
            }
         }
      }

      @Override
      public @Nullable Object getIfUnique() throws BeansException {
         DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
            @Override
            public boolean isRequired() {
               return false;
            }

            @Override
            public boolean usesStandardBeanLookup() {
               return true;
            }

            @Override
            public @Nullable Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
               return null;
            }
         };

         try {
            return this.optional
               ? DefaultListableBeanFactory.this.createOptionalDependency(descriptorToUse, this.beanName, null, null)
               : DefaultListableBeanFactory.this.doResolveDependency(descriptorToUse, this.beanName, null, null);
         } catch (ScopeNotActiveException ex) {
            return null;
         }
      }

      @Override
      public void ifUnique(Consumer<Object> dependencyConsumer) throws BeansException {
         Object dependency = this.getIfUnique();
         if (dependency != null) {
            try {
               dependencyConsumer.accept(dependency);
            } catch (ScopeNotActiveException var4) {
            }
         }
      }

      protected @Nullable Object getValue() throws BeansException {
         Object value = this.cachedValue;
         if (value == null) {
            if (DefaultListableBeanFactory.this.isConfigurationFrozen()) {
               Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
               value = this.resolveValue(autowiredBeanNames);
               boolean cacheable = false;
               if (!autowiredBeanNames.isEmpty()) {
                  cacheable = true;

                  for (String autowiredBeanName : autowiredBeanNames) {
                     if (!DefaultListableBeanFactory.this.containsBean(autowiredBeanName) || !DefaultListableBeanFactory.this.isSingleton(autowiredBeanName)) {
                        cacheable = false;
                     }
                  }
               }

               this.cachedValue = cacheable ? (value != null ? value : NULL_VALUE) : NOT_CACHEABLE;
               return value;
            }
         } else {
            if (value == NULL_VALUE) {
               return null;
            }

            if (value != NOT_CACHEABLE) {
               return value;
            }
         }

         return this.resolveValue(null);
      }

      private @Nullable Object resolveValue(@Nullable Set<String> autowiredBeanNames) {
         return this.optional
            ? DefaultListableBeanFactory.this.createOptionalDependency(this.descriptor, this.beanName, autowiredBeanNames, null)
            : DefaultListableBeanFactory.this.doResolveDependency(this.descriptor, this.beanName, autowiredBeanNames, null);
      }

      @Override
      public Stream<Object> stream() {
         return this.resolveStream(false);
      }

      @Override
      public Stream<Object> orderedStream() {
         return this.resolveStream(true);
      }

      private Stream<Object> resolveStream(boolean ordered) {
         DependencyDescriptor descriptorToUse = new DefaultListableBeanFactory.StreamDependencyDescriptor(this.descriptor, ordered);
         Object result = DefaultListableBeanFactory.this.doResolveDependency(descriptorToUse, this.beanName, null, null);
         return result instanceof Stream stream ? stream : Stream.of(result);
      }

      @Override
      public Stream<Object> stream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
         ResolvableType type = this.descriptor.getResolvableType();
         return Arrays.stream(DefaultListableBeanFactory.this.beanNamesForStream(type, includeNonSingletons, true))
            .filter(name -> AutowireUtils.isAutowireCandidate(DefaultListableBeanFactory.this, name))
            .filter(name -> customFilter.test(DefaultListableBeanFactory.this.getType(name)))
            .map(name -> DefaultListableBeanFactory.this.resolveBean(name, type))
            .filter(bean -> !(bean instanceof NullBean));
      }

      @Override
      public Stream<Object> orderedStream(Predicate<Class<?>> customFilter, boolean includeNonSingletons) {
         ResolvableType type = this.descriptor.getResolvableType();
         String[] beanNames = DefaultListableBeanFactory.this.beanNamesForStream(type, includeNonSingletons, true);
         if (beanNames.length == 0) {
            return Stream.empty();
         }

         Map<String, Object> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);

         for (String beanName : beanNames) {
            if (AutowireUtils.isAutowireCandidate(DefaultListableBeanFactory.this, beanName)
               && customFilter.test(DefaultListableBeanFactory.this.getType(beanName))) {
               Object beanInstance = DefaultListableBeanFactory.this.resolveBean(beanName, type);
               if (!(beanInstance instanceof NullBean)) {
                  matchingBeans.put(beanName, beanInstance);
               }
            }
         }

         return matchingBeans.values().stream().sorted(DefaultListableBeanFactory.this.adaptOrderComparator(matchingBeans));
      }
   }

   private class FactoryAwareOrderSourceProvider implements OrderSourceProvider {
      private final Map<Object, String> instancesToBeanNames;

      public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
         this.instancesToBeanNames = instancesToBeanNames;
      }

      public @Nullable Object getOrderSource(Object obj) {
         String beanName = this.instancesToBeanNames.get(obj);
         if (beanName == null) {
            return null;
         }

         try {
            BeanDefinition beanDefinition = DefaultListableBeanFactory.this.getMergedBeanDefinition(beanName);
            List<Object> sources = new ArrayList<>(3);
            Object orderAttribute = beanDefinition.getAttribute("order");
            if (orderAttribute != null) {
               if (!(orderAttribute instanceof Integer order)) {
                  throw new IllegalStateException("Invalid value type for attribute 'order': " + orderAttribute.getClass().getName());
               }

               sources.add((Ordered)() -> order);
            }

            if (beanDefinition instanceof RootBeanDefinition rootBeanDefinition) {
               Method factoryMethod = rootBeanDefinition.getResolvedFactoryMethod();
               if (factoryMethod != null) {
                  sources.add(factoryMethod);
               }

               Class<?> targetType = rootBeanDefinition.getTargetType();
               if (targetType != null && targetType != obj.getClass()) {
                  sources.add(targetType);
               }
            }

            return sources.toArray();
         } catch (NoSuchBeanDefinitionException ex) {
            return null;
         }
      }
   }

   private class Jsr330Factory implements Serializable {
      public Object createDependencyProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
         return new DefaultListableBeanFactory.Jsr330Factory.Jsr330Provider(descriptor, beanName);
      }

      private class Jsr330Provider extends DefaultListableBeanFactory.DependencyObjectProvider implements Provider<Object> {
         public Jsr330Provider(DependencyDescriptor descriptor, @Nullable String beanName) {
            super(descriptor, beanName);
         }

         public @Nullable Object get() throws BeansException {
            return this.getValue();
         }
      }
   }

   private static class MultiElementDescriptor extends DefaultListableBeanFactory.NestedDependencyDescriptor {
      public MultiElementDescriptor(DependencyDescriptor original) {
         super(original);
      }
   }

   private static class NestedDependencyDescriptor extends DependencyDescriptor {
      public NestedDependencyDescriptor(DependencyDescriptor original) {
         super(original);
         this.increaseNestingLevel();
      }

      @Override
      public boolean usesStandardBeanLookup() {
         return true;
      }
   }

   private enum PreInstantiation {
      MAIN,
      BACKGROUND;
   }

   private static class SerializedBeanFactoryReference implements Serializable {
      private final String id;

      public SerializedBeanFactoryReference(String id) {
         this.id = id;
      }

      private Object readResolve() {
         Reference<?> ref = DefaultListableBeanFactory.serializableFactories.get(this.id);
         if (ref != null) {
            Object result = ref.get();
            if (result != null) {
               return result;
            }
         }

         DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
         dummyFactory.serializationId = this.id;
         return dummyFactory;
      }
   }

   private static class StreamDependencyDescriptor extends DependencyDescriptor {
      private final boolean ordered;

      public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
         super(original);
         this.ordered = ordered;
      }

      public boolean isOrdered() {
         return this.ordered;
      }
   }
}
