package org.springframework.beans.factory.support;

import java.beans.PropertyEditor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
   private @Nullable BeanFactory parentBeanFactory;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable ClassLoader tempClassLoader;
   private boolean cacheBeanMetadata = true;
   private @Nullable BeanExpressionResolver beanExpressionResolver;
   private @Nullable ConversionService conversionService;
   private final Set<PropertyEditorRegistrar> defaultEditorRegistrars = new LinkedHashSet<>(4);
   private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);
   private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);
   private @Nullable TypeConverter typeConverter;
   private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();
   private final List<BeanPostProcessor> beanPostProcessors = new AbstractBeanFactory.BeanPostProcessorCacheAwareList();
   private AbstractBeanFactory.@Nullable BeanPostProcessorCache beanPostProcessorCache;
   private final Map<String, Scope> scopes = new LinkedHashMap<>(8);
   private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
   private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);
   private final Set<String> alreadyCreated = ConcurrentHashMap.newKeySet(256);
   private final ThreadLocal<Object> prototypesCurrentlyInCreation = new NamedThreadLocal("Prototype beans currently in creation");

   public AbstractBeanFactory() {
   }

   public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
      this.parentBeanFactory = parentBeanFactory;
   }

   @Override
   public Object getBean(String name) throws BeansException {
      return this.doGetBean(name, null, null, false);
   }

   @Override
   public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
      return this.doGetBean(name, requiredType, null, false);
   }

   @Override
   public <T> T getBean(String name, ParameterizedTypeReference<T> typeReference) throws BeansException {
      Object bean = this.getBean(name);
      Type requiredType = typeReference.getType();
      if (!this.isTypeMatch(name, ResolvableType.forType(requiredType), true)) {
         throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
      } else {
         return (T)bean;
      }
   }

   @Override
   public Object getBean(String name, @Nullable Object @Nullable ... args) throws BeansException {
      return this.doGetBean(name, null, args, false);
   }

   public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object @Nullable ... args) throws BeansException {
      return this.doGetBean(name, requiredType, args, false);
   }

   protected <T> T doGetBean(String name, @Nullable Class<T> requiredType, @Nullable Object @Nullable [] args, boolean typeCheckOnly) throws BeansException {
      String beanName = this.transformedBeanName(name);
      Object sharedInstance = this.getSingleton(beanName);
      Object beanInstance;
      if (sharedInstance != null && args == null) {
         if (this.logger.isTraceEnabled()) {
            if (this.isSingletonCurrentlyInCreation(beanName)) {
               this.logger
                  .trace(
                     "Returning eagerly cached instance of singleton bean '"
                        + beanName
                        + "' that is not fully initialized yet - a consequence of a circular reference"
                  );
            } else {
               this.logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
            }
         }

         beanInstance = this.getObjectForBeanInstance(sharedInstance, requiredType, name, beanName, null);
      } else {
         if (this.isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
         }

         BeanFactory parentBeanFactory = this.getParentBeanFactory();
         if (parentBeanFactory != null && !this.containsBeanDefinition(beanName)) {
            String nameToLookup = this.originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory abf) {
               return abf.doGetBean(nameToLookup, requiredType, args, typeCheckOnly);
            }

            if (args != null) {
               return (T)parentBeanFactory.getBean(nameToLookup, args);
            }

            if (requiredType != null) {
               return parentBeanFactory.getBean(nameToLookup, requiredType);
            }

            return (T)parentBeanFactory.getBean(nameToLookup);
         }

         if (!typeCheckOnly) {
            this.markBeanAsCreated(beanName);
         }

         StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate").tag("beanName", name);

         try {
            if (requiredType != null) {
               beanCreation.tag("beanType", requiredType::toString);
            }

            RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
            this.checkMergedBeanDefinition(mbd, beanName, args);
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
               for (String dep : dependsOn) {
                  if (this.isDependent(beanName, dep)) {
                     throw new BeanCreationException(
                        mbd.getResourceDescription(), beanName, "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'"
                     );
                  }

                  this.registerDependentBean(dep, beanName);

                  try {
                     this.getBean(dep);
                  } catch (NoSuchBeanDefinitionException ex) {
                     throw new BeanCreationException(mbd.getResourceDescription(), beanName, "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                  } catch (BeanCreationException ex) {
                     if (requiredType != null) {
                        throw new BeanCreationException(
                           mbd.getResourceDescription(),
                           beanName,
                           "Failed to initialize dependency '"
                              + ex.getBeanName()
                              + "' of "
                              + requiredType.getSimpleName()
                              + " bean '"
                              + beanName
                              + "': "
                              + ex.getMessage(),
                           ex
                        );
                     }

                     throw ex;
                  }
               }
            }

            if (mbd.isSingleton()) {
               sharedInstance = this.getSingleton(beanName, () -> {
                  try {
                     return this.createBean(beanName, mbd, args);
                  } catch (BeansException exx) {
                     this.destroySingleton(beanName);
                     throw exx;
                  }
               });
               beanInstance = this.getObjectForBeanInstance(sharedInstance, requiredType, name, beanName, mbd);
            } else if (mbd.isPrototype()) {
               Object prototypeInstance = null;

               try {
                  this.beforePrototypeCreation(beanName);
                  prototypeInstance = this.createBean(beanName, mbd, args);
               } finally {
                  this.afterPrototypeCreation(beanName);
               }

               beanInstance = this.getObjectForBeanInstance(prototypeInstance, requiredType, name, beanName, mbd);
            } else {
               String scopeName = mbd.getScope();
               if (!StringUtils.hasLength(scopeName)) {
                  throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
               }

               Scope scope = this.scopes.get(scopeName);
               if (scope == null) {
                  throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
               }

               try {
                  Object scopedInstance = scope.get(beanName, () -> {
                     this.beforePrototypeCreation(beanName);

                     try {
                        return this.createBean(beanName, mbd, args);
                     } finally {
                        this.afterPrototypeCreation(beanName);
                     }
                  });
                  beanInstance = this.getObjectForBeanInstance(scopedInstance, requiredType, name, beanName, mbd);
               } catch (IllegalStateException ex) {
                  throw new ScopeNotActiveException(beanName, scopeName, ex);
               }
            }
         } catch (BeansException ex) {
            beanCreation.tag("exception", ex.getClass().toString());
            beanCreation.tag("message", String.valueOf(ex.getMessage()));
            this.cleanupAfterBeanCreationFailure(beanName);
            throw ex;
         } finally {
            beanCreation.end();
            if (!this.isCacheBeanMetadata()) {
               this.clearMergedBeanDefinition(beanName);
            }
         }
      }

      return this.adaptBeanInstance(name, beanInstance, requiredType);
   }

   <T> T adaptBeanInstance(String name, Object bean, @Nullable Class<?> requiredType) {
      if (requiredType != null && !requiredType.isInstance(bean)) {
         try {
            Object convertedBean = this.getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
               throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            } else {
               return (T)convertedBean;
            }
         } catch (TypeMismatchException ex) {
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Failed to convert bean '" + name + "' to required type '" + requiredType.getTypeName() + "'", ex);
            }

            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
         }
      } else {
         return (T)bean;
      }
   }

   @Override
   public boolean containsBean(String name) {
      String beanName = this.transformedBeanName(name);
      if (!this.containsSingleton(beanName) && !this.containsBeanDefinition(beanName)) {
         BeanFactory parentBeanFactory = this.getParentBeanFactory();
         return parentBeanFactory != null && parentBeanFactory.containsBean(this.originalBeanName(name));
      } else {
         return !BeanFactoryUtils.isFactoryDereference(name) || this.isFactoryBean(name);
      }
   }

   @Override
   public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
      String beanName = this.transformedBeanName(name);
      Object beanInstance = this.getSingleton(beanName, false);
      if (beanInstance != null) {
         return !(beanInstance instanceof FactoryBean<?> factoryBean)
            ? !BeanFactoryUtils.isFactoryDereference(name)
            : BeanFactoryUtils.isFactoryDereference(name) || factoryBean.isSingleton();
      }

      BeanFactory parentBeanFactory = this.getParentBeanFactory();
      if (parentBeanFactory != null && !this.containsBeanDefinition(beanName)) {
         return parentBeanFactory.isSingleton(this.originalBeanName(name));
      }

      RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
      if (mbd.isSingleton()) {
         if (this.isFactoryBean(beanName, mbd)) {
            if (BeanFactoryUtils.isFactoryDereference(name)) {
               return true;
            }

            FactoryBean<?> factoryBean = (FactoryBean<?>)this.getBean("&" + beanName);
            return factoryBean.isSingleton();
         } else {
            return !BeanFactoryUtils.isFactoryDereference(name);
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
      String beanName = this.transformedBeanName(name);
      BeanFactory parentBeanFactory = this.getParentBeanFactory();
      if (parentBeanFactory != null && !this.containsBeanDefinition(beanName)) {
         return parentBeanFactory.isPrototype(this.originalBeanName(name));
      }

      RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
      if (mbd.isPrototype()) {
         return !BeanFactoryUtils.isFactoryDereference(name) || this.isFactoryBean(beanName, mbd);
      }

      if (BeanFactoryUtils.isFactoryDereference(name)) {
         return false;
      }

      if (!this.isFactoryBean(beanName, mbd)) {
         return false;
      }

      FactoryBean<?> fb = (FactoryBean<?>)this.getBean("&" + beanName);
      return fb instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isPrototype() || !fb.isSingleton();
   }

   @Override
   public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
      return this.isTypeMatch(name, typeToMatch, true);
   }

   protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      String beanName = this.transformedBeanName(name);
      boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);
      Object beanInstance = this.getSingleton(beanName, false);
      if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
         if (beanInstance instanceof FactoryBean<?> factoryBean) {
            if (!isFactoryDereference) {
               Class<?> classToMatch = typeToMatch.resolve();
               if (factoryBean instanceof SmartFactoryBean<?> smartFactoryBean && classToMatch != null && smartFactoryBean.supportsType(classToMatch)) {
                  return true;
               }

               Class<?> type = this.getTypeForFactoryBean(factoryBean);
               if (type == null) {
                  return false;
               }

               if (typeToMatch.isAssignableFrom(type)) {
                  return true;
               }

               if (typeToMatch.hasGenerics() && this.containsBeanDefinition(beanName)) {
                  RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
                  ResolvableType targetType = mbd.targetType;
                  if (targetType == null) {
                     targetType = mbd.factoryMethodReturnType;
                  }

                  if (targetType == null) {
                     return false;
                  }

                  Class<?> targetClass = targetType.resolve();
                  if (targetClass == null || !FactoryBean.class.isAssignableFrom(targetClass)) {
                     return typeToMatch.isAssignableFrom(targetType);
                  }

                  if (classToMatch != null && !FactoryBean.class.isAssignableFrom(classToMatch) && !classToMatch.isAssignableFrom(targetType.toClass())) {
                     return typeToMatch.isAssignableFrom(targetType.getGeneric(new int[0]));
                  }
               }

               return false;
            }
         } else if (isFactoryDereference) {
            return false;
         }

         if (typeToMatch.isInstance(beanInstance)) {
            return true;
         }

         if (typeToMatch.hasGenerics() && this.containsBeanDefinition(beanName)) {
            RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
            Class<?> targetType = mbd.getTargetType();
            if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
               Class<?> classToMatch = typeToMatch.resolve();
               if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
                  return false;
               }

               if (typeToMatch.isAssignableFrom(targetType)) {
                  return true;
               }
            }

            ResolvableType resolvableType = mbd.targetType;
            if (resolvableType == null) {
               resolvableType = mbd.factoryMethodReturnType;
            }

            return resolvableType != null && typeToMatch.isAssignableFrom(resolvableType);
         } else {
            return false;
         }
      } else {
         if (this.containsSingleton(beanName) && !this.containsBeanDefinition(beanName)) {
            return false;
         }

         BeanFactory parentBeanFactory = this.getParentBeanFactory();
         if (parentBeanFactory != null && !this.containsBeanDefinition(beanName)) {
            return parentBeanFactory.isTypeMatch(this.originalBeanName(name), typeToMatch);
         }

         RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
         BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
         Class<?> classToMatch = typeToMatch.resolve();
         if (classToMatch == null) {
            classToMatch = FactoryBean.class;
         }

         Class<?>[] typesToMatch = FactoryBean.class == classToMatch ? new Class[]{classToMatch} : new Class[]{FactoryBean.class, classToMatch};
         Class<?> predictedType = null;
         if (!isFactoryDereference && dbd != null && this.isFactoryBean(beanName, mbd) && (!mbd.isLazyInit() || allowFactoryBeanInit)) {
            RootBeanDefinition tbd = this.getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
            Class<?> targetType = this.predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
            if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
               predictedType = targetType;
            }
         }

         if (predictedType == null) {
            predictedType = this.predictBeanType(beanName, mbd, typesToMatch);
            if (predictedType == null) {
               return false;
            }
         }

         ResolvableType beanType = null;
         if (FactoryBean.class.isAssignableFrom(predictedType)) {
            if (beanInstance == null && !isFactoryDereference) {
               beanType = this.getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
               predictedType = beanType.resolve();
               if (predictedType == null) {
                  return false;
               }
            }
         } else if (isFactoryDereference) {
            predictedType = this.predictBeanType(beanName, mbd, FactoryBean.class);
            if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
               return false;
            }
         }

         if (beanType == null) {
            ResolvableType definedType = mbd.targetType;
            if (definedType == null) {
               definedType = mbd.factoryMethodReturnType;
            }

            if (definedType != null && definedType.resolve() == predictedType) {
               beanType = definedType;
            }
         }

         return beanType != null ? typeToMatch.isAssignableFrom(beanType) : typeToMatch.isAssignableFrom(predictedType);
      }
   }

   @Override
   public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
      return this.isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
   }

   @Override
   public @Nullable Class<?> getType(String name) throws NoSuchBeanDefinitionException {
      return this.getType(name, true);
   }

   @Override
   public @Nullable Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      String beanName = this.transformedBeanName(name);
      Object beanInstance = this.getSingleton(beanName, false);
      if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
         return beanInstance instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name)
            ? this.getTypeForFactoryBean(factoryBean)
            : beanInstance.getClass();
      }

      BeanFactory parentBeanFactory = this.getParentBeanFactory();
      if (parentBeanFactory != null && !this.containsBeanDefinition(beanName)) {
         return parentBeanFactory.getType(this.originalBeanName(name));
      }

      RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
      Class<?> beanClass = this.predictBeanType(beanName, mbd);
      if (beanClass != null) {
         if (FactoryBean.class.isAssignableFrom(beanClass)) {
            if (!BeanFactoryUtils.isFactoryDereference(name)) {
               beanClass = this.getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
            }
         } else if (BeanFactoryUtils.isFactoryDereference(name)) {
            return null;
         }
      }

      if (beanClass == null) {
         BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
         if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
            RootBeanDefinition tbd = this.getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
            Class<?> targetClass = this.predictBeanType(dbd.getBeanName(), tbd);
            if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
               return targetClass;
            }
         }
      }

      return beanClass;
   }

   @Override
   public String[] getAliases(String name) {
      String beanName = this.transformedBeanName(name);
      List<String> aliases = new ArrayList<>();
      boolean hasFactoryPrefix = !name.isEmpty() && name.charAt(0) == '&';
      String fullBeanName = beanName;
      if (hasFactoryPrefix) {
         fullBeanName = "&" + beanName;
      }

      if (!fullBeanName.equals(name)) {
         aliases.add(fullBeanName);
      }

      String[] retrievedAliases = super.getAliases(beanName);
      String prefix = hasFactoryPrefix ? "&" : "";

      for (String retrievedAlias : retrievedAliases) {
         String alias = prefix + retrievedAlias;
         if (!alias.equals(name)) {
            aliases.add(alias);
         }
      }

      if (!this.containsSingleton(beanName) && !this.containsBeanDefinition(beanName)) {
         BeanFactory parentBeanFactory = this.getParentBeanFactory();
         if (parentBeanFactory != null) {
            aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
         }
      }

      return StringUtils.toStringArray(aliases);
   }

   @Override
   public @Nullable BeanFactory getParentBeanFactory() {
      return this.parentBeanFactory;
   }

   @Override
   public boolean containsLocalBean(String name) {
      String beanName = this.transformedBeanName(name);
      return (this.containsSingleton(beanName) || this.containsBeanDefinition(beanName))
         && (!BeanFactoryUtils.isFactoryDereference(name) || this.isFactoryBean(beanName));
   }

   @Override
   public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
      if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
         throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
      }

      if (this == parentBeanFactory) {
         throw new IllegalStateException("Cannot set parent bean factory to self");
      }

      this.parentBeanFactory = parentBeanFactory;
   }

   @Override
   public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader();
   }

   @Override
   public @Nullable ClassLoader getBeanClassLoader() {
      return this.beanClassLoader;
   }

   @Override
   public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
      this.tempClassLoader = tempClassLoader;
   }

   @Override
   public @Nullable ClassLoader getTempClassLoader() {
      return this.tempClassLoader;
   }

   @Override
   public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
      this.cacheBeanMetadata = cacheBeanMetadata;
   }

   @Override
   public boolean isCacheBeanMetadata() {
      return this.cacheBeanMetadata;
   }

   @Override
   public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
      this.beanExpressionResolver = resolver;
   }

   @Override
   public @Nullable BeanExpressionResolver getBeanExpressionResolver() {
      return this.beanExpressionResolver;
   }

   @Override
   public void setConversionService(@Nullable ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   @Override
   public @Nullable ConversionService getConversionService() {
      return this.conversionService;
   }

   @Override
   public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
      Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
      if (registrar.overridesDefaultEditors()) {
         this.defaultEditorRegistrars.add(registrar);
      } else {
         this.propertyEditorRegistrars.add(registrar);
      }
   }

   public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
      return this.propertyEditorRegistrars;
   }

   @Override
   public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
      Assert.notNull(requiredType, "Required type must not be null");
      Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
      this.customEditors.put(requiredType, propertyEditorClass);
   }

   @Override
   public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
      this.registerCustomEditors(registry);
   }

   public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
      return this.customEditors;
   }

   @Override
   public void setTypeConverter(TypeConverter typeConverter) {
      this.typeConverter = typeConverter;
   }

   protected @Nullable TypeConverter getCustomTypeConverter() {
      return this.typeConverter;
   }

   @Override
   public TypeConverter getTypeConverter() {
      TypeConverter customConverter = this.getCustomTypeConverter();
      if (customConverter != null) {
         return customConverter;
      }

      SimpleTypeConverter typeConverter = new SimpleTypeConverter();
      typeConverter.setConversionService(this.getConversionService());
      this.registerCustomEditors(typeConverter);
      return typeConverter;
   }

   @Override
   public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
      Assert.notNull(valueResolver, "StringValueResolver must not be null");
      this.embeddedValueResolvers.add(valueResolver);
   }

   @Override
   public boolean hasEmbeddedValueResolver() {
      return !this.embeddedValueResolvers.isEmpty();
   }

   @Override
   public @Nullable String resolveEmbeddedValue(@Nullable String value) {
      if (value == null) {
         return null;
      }

      String result = value;

      for (StringValueResolver resolver : this.embeddedValueResolvers) {
         result = resolver.resolveStringValue(result);
         if (result == null) {
            return null;
         }
      }

      return result;
   }

   @Override
   public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
      Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
      synchronized (this.beanPostProcessors) {
         this.beanPostProcessors.remove(beanPostProcessor);
         this.beanPostProcessors.add(beanPostProcessor);
      }
   }

   public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
      synchronized (this.beanPostProcessors) {
         this.beanPostProcessors.removeAll(beanPostProcessors);
         this.beanPostProcessors.addAll(beanPostProcessors);
      }
   }

   @Override
   public int getBeanPostProcessorCount() {
      return this.beanPostProcessors.size();
   }

   public List<BeanPostProcessor> getBeanPostProcessors() {
      return this.beanPostProcessors;
   }

   AbstractBeanFactory.BeanPostProcessorCache getBeanPostProcessorCache() {
      synchronized (this.beanPostProcessors) {
         AbstractBeanFactory.BeanPostProcessorCache bppCache = this.beanPostProcessorCache;
         if (bppCache == null) {
            bppCache = new AbstractBeanFactory.BeanPostProcessorCache();

            for (BeanPostProcessor bpp : this.beanPostProcessors) {
               if (bpp instanceof InstantiationAwareBeanPostProcessor instantiationAwareBpp) {
                  bppCache.instantiationAware.add(instantiationAwareBpp);
                  if (bpp instanceof SmartInstantiationAwareBeanPostProcessor smartInstantiationAwareBpp) {
                     bppCache.smartInstantiationAware.add(smartInstantiationAwareBpp);
                  }
               }

               if (bpp instanceof DestructionAwareBeanPostProcessor destructionAwareBpp) {
                  bppCache.destructionAware.add(destructionAwareBpp);
               }

               if (bpp instanceof MergedBeanDefinitionPostProcessor mergedBeanDefBpp) {
                  bppCache.mergedDefinition.add(mergedBeanDefBpp);
               }
            }

            this.beanPostProcessorCache = bppCache;
         }

         return bppCache;
      }
   }

   private void resetBeanPostProcessorCache() {
      synchronized (this.beanPostProcessors) {
         this.beanPostProcessorCache = null;
      }
   }

   protected boolean hasInstantiationAwareBeanPostProcessors() {
      return !this.getBeanPostProcessorCache().instantiationAware.isEmpty();
   }

   protected boolean hasDestructionAwareBeanPostProcessors() {
      return !this.getBeanPostProcessorCache().destructionAware.isEmpty();
   }

   @Override
   public void registerScope(String scopeName, Scope scope) {
      Assert.notNull(scopeName, "Scope identifier must not be null");
      Assert.notNull(scope, "Scope must not be null");
      if (!"singleton".equals(scopeName) && !"prototype".equals(scopeName)) {
         Scope previous = this.scopes.put(scopeName, scope);
         if (previous != null && previous != scope) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
            }
         } else if (this.logger.isTraceEnabled()) {
            this.logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
         }
      } else {
         throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
      }
   }

   @Override
   public String[] getRegisteredScopeNames() {
      return StringUtils.toStringArray(this.scopes.keySet());
   }

   @Override
   public @Nullable Scope getRegisteredScope(String scopeName) {
      Assert.notNull(scopeName, "Scope identifier must not be null");
      return this.scopes.get(scopeName);
   }

   @Override
   public void setApplicationStartup(ApplicationStartup applicationStartup) {
      Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
      this.applicationStartup = applicationStartup;
   }

   @Override
   public ApplicationStartup getApplicationStartup() {
      return this.applicationStartup;
   }

   @Override
   public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
      Assert.notNull(otherFactory, "BeanFactory must not be null");
      this.setBeanClassLoader(otherFactory.getBeanClassLoader());
      this.setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
      this.setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
      this.setConversionService(otherFactory.getConversionService());
      if (otherFactory instanceof AbstractBeanFactory otherAbstractFactory) {
         this.defaultEditorRegistrars.addAll(otherAbstractFactory.defaultEditorRegistrars);
         this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
         this.customEditors.putAll(otherAbstractFactory.customEditors);
         this.typeConverter = otherAbstractFactory.typeConverter;
         this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
         this.scopes.putAll(otherAbstractFactory.scopes);
      } else {
         this.setTypeConverter(otherFactory.getTypeConverter());
         String[] otherScopeNames = otherFactory.getRegisteredScopeNames();

         for (String scopeName : otherScopeNames) {
            this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
         }
      }
   }

   @Override
   public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
      String beanName = this.transformedBeanName(name);
      return this.getParentBeanFactory() instanceof ConfigurableBeanFactory parent && !this.containsBeanDefinition(beanName)
         ? parent.getMergedBeanDefinition(beanName)
         : this.getMergedLocalBeanDefinition(beanName);
   }

   @Override
   public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
      String beanName = this.transformedBeanName(name);
      Object beanInstance = this.getSingleton(beanName, false);
      if (beanInstance != null) {
         return beanInstance instanceof FactoryBean;
      } else {
         return !this.containsBeanDefinition(beanName) && this.getParentBeanFactory() instanceof ConfigurableBeanFactory cbf
            ? cbf.isFactoryBean(name)
            : this.isFactoryBean(beanName, this.getMergedLocalBeanDefinition(beanName));
      }
   }

   @Override
   public boolean isActuallyInCreation(String beanName) {
      return this.isSingletonCurrentlyInCreation(beanName) || this.isPrototypeCurrentlyInCreation(beanName);
   }

   protected boolean isPrototypeCurrentlyInCreation(String beanName) {
      Object curVal = this.prototypesCurrentlyInCreation.get();
      return curVal != null && (curVal.equals(beanName) || curVal instanceof Set<?> set && set.contains(beanName));
   }

   protected void beforePrototypeCreation(String beanName) {
      Object curVal = this.prototypesCurrentlyInCreation.get();
      if (curVal == null) {
         this.prototypesCurrentlyInCreation.set(beanName);
      } else if (curVal instanceof String strValue) {
         Set<String> beanNameSet = CollectionUtils.newHashSet(2);
         beanNameSet.add(strValue);
         beanNameSet.add(beanName);
         this.prototypesCurrentlyInCreation.set(beanNameSet);
      } else {
         Set<String> beanNameSet = (Set<String>)curVal;
         beanNameSet.add(beanName);
      }
   }

   protected void afterPrototypeCreation(String beanName) {
      Object curVal = this.prototypesCurrentlyInCreation.get();
      if (curVal instanceof String) {
         this.prototypesCurrentlyInCreation.remove();
      } else if (curVal instanceof Set<?> beanNameSet) {
         beanNameSet.remove(beanName);
         if (beanNameSet.isEmpty()) {
            this.prototypesCurrentlyInCreation.remove();
         }
      }
   }

   @Override
   public void destroyBean(String beanName, Object beanInstance) {
      this.destroyBean(beanName, beanInstance, this.getMergedLocalBeanDefinition(beanName));
   }

   protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
      new DisposableBeanAdapter(bean, beanName, mbd, this.getBeanPostProcessorCache().destructionAware).destroy();
   }

   @Override
   public void destroyScopedBean(String beanName) {
      RootBeanDefinition mbd = this.getMergedLocalBeanDefinition(beanName);
      if (!mbd.isSingleton() && !mbd.isPrototype()) {
         String scopeName = mbd.getScope();
         Scope scope = this.scopes.get(scopeName);
         if (scope == null) {
            throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
         }

         Object bean = scope.remove(beanName);
         if (bean != null) {
            this.destroyBean(beanName, bean, mbd);
         }
      } else {
         throw new IllegalArgumentException("Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
      }
   }

   protected String transformedBeanName(String name) {
      return this.canonicalName(BeanFactoryUtils.transformedBeanName(name));
   }

   protected String originalBeanName(String name) {
      String beanName = this.transformedBeanName(name);
      if (!name.isEmpty() && name.charAt(0) == '&') {
         beanName = "&" + beanName;
      }

      return beanName;
   }

   protected void initBeanWrapper(BeanWrapper bw) {
      bw.setConversionService(this.getConversionService());
      this.registerCustomEditors(bw);
   }

   protected void registerCustomEditors(PropertyEditorRegistry registry) {
      if (registry instanceof PropertyEditorRegistrySupport registrySupport) {
         registrySupport.useConfigValueEditors();
         if (!this.defaultEditorRegistrars.isEmpty()) {
            registrySupport.setDefaultEditorRegistrar(new AbstractBeanFactory.BeanFactoryDefaultEditorRegistrar());
         }
      } else if (!this.defaultEditorRegistrars.isEmpty()) {
         this.applyEditorRegistrars(registry, this.defaultEditorRegistrars);
      }

      if (!this.propertyEditorRegistrars.isEmpty()) {
         this.applyEditorRegistrars(registry, this.propertyEditorRegistrars);
      }

      if (!this.customEditors.isEmpty()) {
         this.customEditors
            .forEach(
               (requiredType, editorClass) -> registry.registerCustomEditor(
                  (Class<?>)requiredType, BeanUtils.instantiateClass((Class<PropertyEditor>)editorClass)
               )
            );
      }
   }

   private void applyEditorRegistrars(PropertyEditorRegistry registry, Set<PropertyEditorRegistrar> registrars) {
      for (PropertyEditorRegistrar registrar : registrars) {
         try {
            registrar.registerCustomEditors(registry);
         } catch (BeanCreationException ex) {
            if (ex.getMostSpecificCause() instanceof BeanCurrentlyInCreationException bce) {
               String bceBeanName = bce.getBeanName();
               if (bceBeanName != null && this.isCurrentlyInCreation(bceBeanName)) {
                  if (this.logger.isDebugEnabled()) {
                     this.logger
                        .debug(
                           "PropertyEditorRegistrar ["
                              + registrar.getClass().getName()
                              + "] failed because it tried to obtain currently created bean '"
                              + ex.getBeanName()
                              + "': "
                              + ex.getMessage()
                        );
                  }

                  this.onSuppressedException(ex);
                  return;
               }
            }

            throw ex;
         }
      }
   }

   protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
      RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
      return mbd != null && !mbd.stale ? mbd : this.getMergedBeanDefinition(beanName, this.getBeanDefinition(beanName));
   }

   protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd) throws BeanDefinitionStoreException {
      return this.getMergedBeanDefinition(beanName, bd, null);
   }

   protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd) throws BeanDefinitionStoreException {
      synchronized (this.mergedBeanDefinitions) {
         RootBeanDefinition mbd = null;
         RootBeanDefinition previous = null;
         if (containingBd == null) {
            mbd = this.mergedBeanDefinitions.get(beanName);
         }

         if (mbd == null || mbd.stale) {
            previous = mbd;
            if (bd.getParentName() == null) {
               if (bd instanceof RootBeanDefinition rootBeanDef) {
                  mbd = rootBeanDef.cloneBeanDefinition();
               } else {
                  mbd = new RootBeanDefinition(bd);
               }
            } else {
               BeanDefinition pbd;
               try {
                  String parentBeanName = this.transformedBeanName(bd.getParentName());
                  if (!beanName.equals(parentBeanName)) {
                     pbd = this.getMergedBeanDefinition(parentBeanName);
                  } else {
                     if (!(this.getParentBeanFactory() instanceof ConfigurableBeanFactory parent)) {
                        throw new NoSuchBeanDefinitionException(
                           parentBeanName,
                           "Parent name '"
                              + parentBeanName
                              + "' is equal to bean name '"
                              + beanName
                              + "': cannot be resolved without a ConfigurableBeanFactory parent"
                        );
                     }

                     pbd = parent.getMergedBeanDefinition(parentBeanName);
                  }
               } catch (NoSuchBeanDefinitionException ex) {
                  throw new BeanDefinitionStoreException(
                     bd.getResourceDescription(), beanName, "Could not resolve parent bean definition '" + bd.getParentName() + "'", ex
                  );
               }

               mbd = new RootBeanDefinition(pbd);
               mbd.overrideFrom(bd);
            }

            if (!StringUtils.hasLength(mbd.getScope())) {
               mbd.setScope("singleton");
            }

            if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
               mbd.setScope(containingBd.getScope());
            }

            if (containingBd == null && (this.isCacheBeanMetadata() || this.isBeanEligibleForMetadataCaching(beanName))) {
               this.cacheMergedBeanDefinition(mbd, beanName);
            }
         }

         if (previous != null) {
            this.copyRelevantMergedBeanDefinitionCaches(previous, mbd);
         }

         return mbd;
      }
   }

   private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
      if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName())
         && ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName())
         && ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
         ResolvableType targetType = mbd.targetType;
         ResolvableType previousTargetType = previous.targetType;
         if (targetType == null || targetType.equals(previousTargetType)) {
            mbd.targetType = previousTargetType;
            mbd.isFactoryBean = previous.isFactoryBean;
            mbd.resolvedTargetType = previous.resolvedTargetType;
            mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
            mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
         }

         if (previous.hasMethodOverrides()) {
            mbd.setMethodOverrides(new MethodOverrides(previous.getMethodOverrides()));
         }
      }
   }

   protected void cacheMergedBeanDefinition(RootBeanDefinition mbd, String beanName) {
      this.mergedBeanDefinitions.put(beanName, mbd);
   }

   protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object @Nullable [] args) {
      if (mbd.isAbstract()) {
         throw new BeanIsAbstractException(beanName);
      }
   }

   protected void clearMergedBeanDefinition(String beanName) {
      RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
      if (bd != null) {
         bd.stale = true;
      }
   }

   public void clearMetadataCache() {
      this.mergedBeanDefinitions.forEach((beanName, bd) -> {
         if (!this.isBeanEligibleForMetadataCaching(beanName)) {
            bd.stale = true;
         }
      });
   }

   protected @Nullable Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch) throws CannotLoadBeanClassException {
      try {
         if (mbd.hasBeanClass()) {
            return mbd.getBeanClass();
         }

         Class<?> beanClass = this.doResolveBeanClass(mbd, typesToMatch);
         if (mbd.hasBeanClass()) {
            mbd.prepareMethodOverrides();
         }

         return beanClass;
      } catch (ClassNotFoundException ex) {
         throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
      } catch (LinkageError err) {
         throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
      } catch (BeanDefinitionValidationException ex) {
         throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName, "Validation of method overrides failed", ex);
      }
   }

   private @Nullable Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch) throws ClassNotFoundException {
      ClassLoader beanClassLoader = this.getBeanClassLoader();
      ClassLoader dynamicLoader = beanClassLoader;
      boolean freshResolve = false;
      if (!ObjectUtils.isEmpty(typesToMatch)) {
         ClassLoader tempClassLoader = this.getTempClassLoader();
         if (tempClassLoader != null) {
            dynamicLoader = tempClassLoader;
            freshResolve = true;
            if (tempClassLoader instanceof DecoratingClassLoader dcl) {
               for (Class<?> typeToMatch : typesToMatch) {
                  dcl.excludeClass(typeToMatch.getName());
               }
            }
         }
      }

      String className = mbd.getBeanClassName();
      if (className != null) {
         Object evaluated = this.evaluateBeanDefinitionString(className, mbd);
         if (!className.equals(evaluated)) {
            if (evaluated instanceof Class<?> clazz) {
               return clazz;
            }

            if (!(evaluated instanceof String name)) {
               throw new IllegalStateException("Invalid class name expression result: " + evaluated);
            }

            className = name;
            freshResolve = true;
         }

         if (freshResolve) {
            if (dynamicLoader != null) {
               try {
                  return dynamicLoader.loadClass(className);
               } catch (ClassNotFoundException ex) {
                  if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
                  }
               }
            }

            return ClassUtils.forName(className, dynamicLoader);
         }
      }

      return mbd.resolveBeanClass(beanClassLoader);
   }

   protected @Nullable Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
      if (this.beanExpressionResolver == null) {
         return value;
      }

      Scope scope = null;
      if (beanDefinition != null) {
         String scopeName = beanDefinition.getScope();
         if (scopeName != null) {
            scope = this.getRegisteredScope(scopeName);
         }
      }

      return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
   }

   protected @Nullable Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
      Class<?> targetType = mbd.getTargetType();
      if (targetType != null) {
         return targetType;
      } else {
         return mbd.getFactoryMethodName() != null ? null : this.resolveBeanClass(mbd, beanName, typesToMatch);
      }
   }

   protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
      Boolean result = mbd.isFactoryBean;
      if (result == null) {
         Class<?> beanType = this.predictBeanType(beanName, mbd, FactoryBean.class);
         result = beanType != null && FactoryBean.class.isAssignableFrom(beanType);
         mbd.isFactoryBean = result;
      }

      return result;
   }

   protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
      try {
         ResolvableType result = this.getTypeForFactoryBeanFromAttributes(mbd);
         if (result != ResolvableType.NONE) {
            return result;
         }
      } catch (IllegalArgumentException ex) {
         throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName, String.valueOf(ex.getMessage()));
      }

      if (allowInit && mbd.isSingleton()) {
         try {
            FactoryBean<?> factoryBean = this.doGetBean("&" + beanName, FactoryBean.class, null, true);
            Class<?> objectType = this.getTypeForFactoryBean(factoryBean);
            return objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE;
         } catch (BeanCreationException ex) {
            if (ex.contains(BeanCurrentlyInCreationException.class)) {
               this.logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
            } else if (mbd.isLazyInit()) {
               this.logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
            } else {
               this.logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
            }

            this.onSuppressedException(ex);
         }
      }

      return ResolvableType.NONE;
   }

   protected void markBeanAsCreated(String beanName) {
      if (!this.alreadyCreated.contains(beanName)) {
         synchronized (this.mergedBeanDefinitions) {
            if (!this.isBeanEligibleForMetadataCaching(beanName)) {
               this.clearMergedBeanDefinition(beanName);
            }

            this.alreadyCreated.add(beanName);
         }
      }
   }

   protected void cleanupAfterBeanCreationFailure(String beanName) {
      synchronized (this.mergedBeanDefinitions) {
         this.alreadyCreated.remove(beanName);
      }
   }

   protected boolean isBeanEligibleForMetadataCaching(String beanName) {
      return this.alreadyCreated.contains(beanName);
   }

   protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
      if (!this.alreadyCreated.contains(beanName)) {
         this.removeSingleton(beanName);
         return true;
      } else {
         return false;
      }
   }

   protected boolean hasBeanCreationStarted() {
      return !this.alreadyCreated.isEmpty();
   }

   protected Object getObjectForBeanInstance(
      Object beanInstance, @Nullable Class<?> requiredType, String name, String beanName, @Nullable RootBeanDefinition mbd
   ) {
      if (BeanFactoryUtils.isFactoryDereference(name)) {
         if (beanInstance instanceof NullBean) {
            return beanInstance;
         }

         if (!(beanInstance instanceof FactoryBean)) {
            throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
         }

         if (mbd != null) {
            mbd.isFactoryBean = true;
         }

         return beanInstance;
      } else if (beanInstance instanceof FactoryBean<?> factoryBean) {
         Object object = null;
         if (mbd != null) {
            mbd.isFactoryBean = true;
         } else {
            object = this.getCachedObjectForFactoryBean(beanName);
         }

         if (object == null) {
            if (mbd == null && this.containsBeanDefinition(beanName)) {
               mbd = this.getMergedLocalBeanDefinition(beanName);
            }

            boolean synthetic = mbd != null && mbd.isSynthetic();
            object = this.getObjectFromFactoryBean(factoryBean, requiredType, beanName, !synthetic);
         }

         return object;
      } else {
         return beanInstance;
      }
   }

   public boolean isBeanNameInUse(String beanName) {
      return this.isAlias(beanName) || this.containsLocalBean(beanName) || this.hasDependentBean(beanName);
   }

   protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
      return bean.getClass() != NullBean.class
         && (
            DisposableBeanAdapter.hasDestroyMethod(bean, mbd)
               || this.hasDestructionAwareBeanPostProcessors()
                  && DisposableBeanAdapter.hasApplicableProcessors(bean, this.getBeanPostProcessorCache().destructionAware)
         );
   }

   protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
      if (!mbd.isPrototype() && this.requiresDestruction(bean, mbd)) {
         if (mbd.isSingleton()) {
            this.registerDisposableBean(beanName, new DisposableBeanAdapter(bean, beanName, mbd, this.getBeanPostProcessorCache().destructionAware));
         } else {
            Scope scope = this.scopes.get(mbd.getScope());
            if (scope == null) {
               throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
            }

            scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(bean, beanName, mbd, this.getBeanPostProcessorCache().destructionAware));
         }
      }
   }

   protected abstract boolean containsBeanDefinition(String beanName);

   protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

   protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object @Nullable [] args) throws BeanCreationException;

   class BeanFactoryDefaultEditorRegistrar implements PropertyEditorRegistrar {
      @Override
      public void registerCustomEditors(PropertyEditorRegistry registry) {
         AbstractBeanFactory.this.applyEditorRegistrars(registry, AbstractBeanFactory.this.defaultEditorRegistrars);
      }
   }

   static class BeanPostProcessorCache {
      final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();
      final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();
      final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();
      final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
   }

   private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {
      public BeanPostProcessor set(int index, BeanPostProcessor element) {
         BeanPostProcessor result = super.set(index, element);
         AbstractBeanFactory.this.resetBeanPostProcessorCache();
         return result;
      }

      public boolean add(BeanPostProcessor o) {
         boolean success = super.add(o);
         AbstractBeanFactory.this.resetBeanPostProcessorCache();
         return success;
      }

      public void add(int index, BeanPostProcessor element) {
         super.add(index, element);
         AbstractBeanFactory.this.resetBeanPostProcessorCache();
      }

      public BeanPostProcessor remove(int index) {
         BeanPostProcessor result = (BeanPostProcessor)super.remove(index);
         AbstractBeanFactory.this.resetBeanPostProcessorCache();
         return result;
      }

      @Override
      public boolean remove(Object o) {
         boolean success = super.remove(o);
         if (success) {
            AbstractBeanFactory.this.resetBeanPostProcessorCache();
         }

         return success;
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         boolean success = super.removeAll(c);
         if (success) {
            AbstractBeanFactory.this.resetBeanPostProcessorCache();
         }

         return success;
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         boolean success = super.retainAll(c);
         if (success) {
            AbstractBeanFactory.this.resetBeanPostProcessorCache();
         }

         return success;
      }

      @Override
      public boolean addAll(Collection<? extends BeanPostProcessor> c) {
         boolean success = super.addAll(c);
         if (success) {
            AbstractBeanFactory.this.resetBeanPostProcessorCache();
         }

         return success;
      }

      @Override
      public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
         boolean success = super.addAll(index, c);
         if (success) {
            AbstractBeanFactory.this.resetBeanPostProcessorCache();
         }

         return success;
      }

      @Override
      public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
         boolean success = super.removeIf(filter);
         if (success) {
            AbstractBeanFactory.this.resetBeanPostProcessorCache();
         }

         return success;
      }

      @Override
      public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
         super.replaceAll(operator);
         AbstractBeanFactory.this.resetBeanPostProcessorCache();
      }
   }
}
