package org.springframework.beans.factory.support;

import java.beans.ConstructorProperties;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.CollectionFactory;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MethodInvoker;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

class ConstructorResolver {
   private static final Object[] EMPTY_ARGS = new Object[0];
   private static final NamedThreadLocal<InjectionPoint> currentInjectionPoint = new NamedThreadLocal("Current injection point");
   private final AbstractAutowireCapableBeanFactory beanFactory;
   private final Log logger;

   public ConstructorResolver(AbstractAutowireCapableBeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      this.logger = beanFactory.getLogger();
   }

   public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd, Constructor<?>[] chosenCtors, Object[] explicitArgs) {
      BeanWrapperImpl bw = new BeanWrapperImpl();
      this.beanFactory.initBeanWrapper(bw);
      Constructor<?> constructorToUse = null;
      ConstructorResolver.ArgumentsHolder argsHolderToUse = null;
      Object[] argsToUse = null;
      if (explicitArgs != null) {
         argsToUse = explicitArgs;
      } else {
         Object[] argsToResolve = null;
         synchronized (mbd.constructorArgumentLock) {
            constructorToUse = (Constructor<?>)mbd.resolvedConstructorOrFactoryMethod;
            if (constructorToUse != null && mbd.constructorArgumentsResolved) {
               argsToUse = mbd.resolvedConstructorArguments;
               if (argsToUse == null) {
                  argsToResolve = mbd.preparedConstructorArguments;
               }
            }
         }

         if (argsToResolve != null) {
            argsToUse = this.resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve);
         }
      }

      if (constructorToUse == null || argsToUse == null) {
         Constructor<?>[] candidates = chosenCtors;
         if (candidates == null) {
            Class<?> beanClass = mbd.getBeanClass();

            try {
               candidates = mbd.isNonPublicAccessAllowed() ? beanClass.getDeclaredConstructors() : beanClass.getConstructors();
            } catch (Throwable ex) {
               throw new BeanCreationException(
                  mbd.getResourceDescription(),
                  beanName,
                  "Resolution of declared constructors on bean Class ["
                     + beanClass.getName()
                     + "] from ClassLoader ["
                     + beanClass.getClassLoader()
                     + "] failed",
                  ex
               );
            }
         }

         if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
            Constructor<?> uniqueCandidate = candidates[0];
            if (uniqueCandidate.getParameterCount() == 0) {
               synchronized (mbd.constructorArgumentLock) {
                  mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
                  mbd.constructorArgumentsResolved = true;
                  mbd.resolvedConstructorArguments = EMPTY_ARGS;
               }

               bw.setBeanInstance(this.instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
               return bw;
            }
         }

         boolean autowiring = chosenCtors != null || mbd.getResolvedAutowireMode() == 3;
         ConstructorArgumentValues resolvedValues = null;
         int minNrOfArgs;
         if (explicitArgs != null) {
            minNrOfArgs = explicitArgs.length;
         } else {
            ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
            resolvedValues = new ConstructorArgumentValues();
            minNrOfArgs = this.resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
         }

         AutowireUtils.sortConstructors(candidates);
         int minTypeDiffWeight = Integer.MAX_VALUE;
         Set<Constructor<?>> ambiguousConstructors = null;
         Deque<UnsatisfiedDependencyException> causes = null;

         for (Constructor<?> candidate : candidates) {
            int parameterCount = candidate.getParameterCount();
            if (constructorToUse != null && argsToUse != null && argsToUse.length > parameterCount) {
               break;
            }

            if (parameterCount >= minNrOfArgs) {
               Class<?>[] paramTypes = candidate.getParameterTypes();
               ConstructorResolver.ArgumentsHolder argsHolder;
               if (resolvedValues != null) {
                  try {
                     String[] paramNames = null;
                     if (resolvedValues.containsNamedArgument()) {
                        paramNames = ConstructorResolver.ConstructorPropertiesChecker.evaluate(candidate, parameterCount);
                        if (paramNames == null) {
                           ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
                           if (pnd != null) {
                              paramNames = pnd.getParameterNames(candidate);
                           }
                        }
                     }

                     argsHolder = this.createArgumentArray(
                        beanName,
                        mbd,
                        resolvedValues,
                        bw,
                        paramTypes,
                        paramNames,
                        this.getUserDeclaredConstructor(candidate),
                        autowiring,
                        candidates.length == 1
                     );
                  } catch (UnsatisfiedDependencyException ex) {
                     if (this.logger.isTraceEnabled()) {
                        this.logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
                     }

                     if (causes == null) {
                        causes = new ArrayDeque<>(1);
                     }

                     causes.add(ex);
                     continue;
                  }
               } else {
                  if (parameterCount != explicitArgs.length) {
                     continue;
                  }

                  argsHolder = new ConstructorResolver.ArgumentsHolder(explicitArgs);
               }

               int typeDiffWeight = mbd.isLenientConstructorResolution()
                  ? argsHolder.getTypeDifferenceWeight(paramTypes)
                  : argsHolder.getAssignabilityWeight(paramTypes);
               if (typeDiffWeight < minTypeDiffWeight) {
                  constructorToUse = candidate;
                  argsHolderToUse = argsHolder;
                  argsToUse = argsHolder.arguments;
                  minTypeDiffWeight = typeDiffWeight;
                  ambiguousConstructors = null;
               } else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
                  if (ambiguousConstructors == null) {
                     ambiguousConstructors = new LinkedHashSet<>();
                     ambiguousConstructors.add(constructorToUse);
                  }

                  ambiguousConstructors.add(candidate);
               }
            }
         }

         if (constructorToUse == null) {
            if (causes == null) {
               throw new BeanCreationException(
                  mbd.getResourceDescription(),
                  beanName,
                  "Could not resolve matching constructor on bean class ["
                     + mbd.getBeanClassName()
                     + "] (hint: specify index/type/name arguments for simple parameters to avoid type ambiguities. You should also check the consistency of arguments when mixing indexed and named arguments, especially in case of bean definition inheritance)"
               );
            }

            UnsatisfiedDependencyException ex = causes.removeLast();

            for (Exception cause : causes) {
               this.beanFactory.onSuppressedException(cause);
            }

            throw ex;
         }

         if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
            throw new BeanCreationException(
               mbd.getResourceDescription(),
               beanName,
               "Ambiguous constructor matches found on bean class ["
                  + mbd.getBeanClassName()
                  + "] (hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): "
                  + ambiguousConstructors
            );
         }

         if (explicitArgs == null && argsHolderToUse != null) {
            argsHolderToUse.storeCache(mbd, constructorToUse);
         }
      }

      Assert.state(argsToUse != null, "Unresolved constructor arguments");
      bw.setBeanInstance(this.instantiate(beanName, mbd, constructorToUse, argsToUse));
      return bw;
   }

   private Object instantiate(String beanName, RootBeanDefinition mbd, Constructor<?> constructorToUse, Object[] argsToUse) {
      try {
         InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
         return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
      } catch (Throwable ex) {
         throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
      }
   }

   public void resolveFactoryMethodIfPossible(RootBeanDefinition mbd) {
      Class<?> factoryClass;
      boolean isStatic;
      if (mbd.getFactoryBeanName() != null) {
         factoryClass = this.beanFactory.getType(mbd.getFactoryBeanName());
         isStatic = false;
      } else {
         factoryClass = mbd.getBeanClass();
         isStatic = true;
      }

      Assert.state(factoryClass != null, "Unresolvable factory class");
      factoryClass = ClassUtils.getUserClass(factoryClass);
      Method[] candidates = this.getCandidateMethods(factoryClass, mbd);
      Method uniqueCandidate = null;

      for (Method candidate : candidates) {
         if ((!isStatic || this.isStaticCandidate(candidate, factoryClass)) && mbd.isFactoryMethod(candidate)) {
            if (uniqueCandidate == null) {
               uniqueCandidate = candidate;
            } else if (this.isParamMismatch(uniqueCandidate, candidate)) {
               uniqueCandidate = null;
               break;
            }
         }
      }

      mbd.factoryMethodToIntrospect = uniqueCandidate;
   }

   private boolean isParamMismatch(Method uniqueCandidate, Method candidate) {
      int uniqueCandidateParameterCount = uniqueCandidate.getParameterCount();
      int candidateParameterCount = candidate.getParameterCount();
      return uniqueCandidateParameterCount != candidateParameterCount || !Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes());
   }

   private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
      return mbd.isNonPublicAccessAllowed() ? ReflectionUtils.getUniqueDeclaredMethods(factoryClass) : factoryClass.getMethods();
   }

   private boolean isStaticCandidate(Method method, Class<?> factoryClass) {
      return Modifier.isStatic(method.getModifiers()) && method.getDeclaringClass() == factoryClass;
   }

   public BeanWrapper instantiateUsingFactoryMethod(String beanName, RootBeanDefinition mbd, Object[] explicitArgs) {
      BeanWrapperImpl bw = new BeanWrapperImpl();
      this.beanFactory.initBeanWrapper(bw);
      String factoryBeanName = mbd.getFactoryBeanName();
      Object factoryBean;
      Class<?> factoryClass;
      boolean isStatic;
      if (factoryBeanName != null) {
         if (factoryBeanName.equals(beanName)) {
            throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName, "factory-bean reference points back to the same bean definition");
         }

         factoryBean = this.beanFactory.getBean(factoryBeanName);
         if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
            throw new ImplicitlyAppearedSingletonException();
         }

         this.beanFactory.registerDependentBean(factoryBeanName, beanName);
         factoryClass = factoryBean.getClass();
         isStatic = false;
      } else {
         if (!mbd.hasBeanClass()) {
            throw new BeanDefinitionStoreException(
               mbd.getResourceDescription(), beanName, "bean definition declares neither a bean class nor a factory-bean reference"
            );
         }

         factoryBean = null;
         factoryClass = mbd.getBeanClass();
         isStatic = true;
      }

      Method factoryMethodToUse = null;
      ConstructorResolver.ArgumentsHolder argsHolderToUse = null;
      Object[] argsToUse = null;
      if (explicitArgs != null) {
         argsToUse = explicitArgs;
      } else {
         Object[] argsToResolve = null;
         synchronized (mbd.constructorArgumentLock) {
            factoryMethodToUse = (Method)mbd.resolvedConstructorOrFactoryMethod;
            if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
               argsToUse = mbd.resolvedConstructorArguments;
               if (argsToUse == null) {
                  argsToResolve = mbd.preparedConstructorArguments;
               }
            }
         }

         if (argsToResolve != null) {
            argsToUse = this.resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve);
         }
      }

      if (factoryMethodToUse == null || argsToUse == null) {
         factoryClass = ClassUtils.getUserClass(factoryClass);
         List<Method> candidates = null;
         if (mbd.isFactoryMethodUnique) {
            if (factoryMethodToUse == null) {
               factoryMethodToUse = mbd.getResolvedFactoryMethod();
            }

            if (factoryMethodToUse != null) {
               candidates = Collections.singletonList(factoryMethodToUse);
            }
         }

         if (candidates == null) {
            candidates = new ArrayList<>();
            Method[] rawCandidates = this.getCandidateMethods(factoryClass, mbd);

            for (Method candidate : rawCandidates) {
               if ((!isStatic || this.isStaticCandidate(candidate, factoryClass)) && mbd.isFactoryMethod(candidate)) {
                  candidates.add(candidate);
               }
            }
         }

         if (candidates.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
            Method uniqueCandidate = candidates.get(0);
            if (uniqueCandidate.getParameterCount() == 0) {
               mbd.factoryMethodToIntrospect = uniqueCandidate;
               synchronized (mbd.constructorArgumentLock) {
                  mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
                  mbd.constructorArgumentsResolved = true;
                  mbd.resolvedConstructorArguments = EMPTY_ARGS;
               }

               bw.setBeanInstance(this.instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));
               return bw;
            }
         }

         if (candidates.size() > 1) {
            candidates.sort(AutowireUtils.EXECUTABLE_COMPARATOR);
         }

         ConstructorArgumentValues resolvedValues = null;
         boolean autowiring = mbd.getResolvedAutowireMode() == 3;
         int minTypeDiffWeight = Integer.MAX_VALUE;
         Set<Method> ambiguousFactoryMethods = null;
         int minNrOfArgs;
         if (explicitArgs != null) {
            minNrOfArgs = explicitArgs.length;
         } else if (mbd.hasConstructorArgumentValues()) {
            ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
            resolvedValues = new ConstructorArgumentValues();
            minNrOfArgs = this.resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
         } else {
            minNrOfArgs = 0;
         }

         Deque<UnsatisfiedDependencyException> causes = null;

         for (Method candidate : candidates) {
            int parameterCount = candidate.getParameterCount();
            if (parameterCount >= minNrOfArgs) {
               Class<?>[] paramTypes = candidate.getParameterTypes();
               ConstructorResolver.ArgumentsHolder argsHolder;
               if (explicitArgs != null) {
                  if (paramTypes.length != explicitArgs.length) {
                     continue;
                  }

                  argsHolder = new ConstructorResolver.ArgumentsHolder(explicitArgs);
               } else {
                  try {
                     String[] paramNames = null;
                     if (resolvedValues != null && resolvedValues.containsNamedArgument()) {
                        ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
                        if (pnd != null) {
                           paramNames = pnd.getParameterNames(candidate);
                        }
                     }

                     argsHolder = this.createArgumentArray(
                        beanName, mbd, resolvedValues, bw, paramTypes, paramNames, candidate, autowiring, candidates.size() == 1
                     );
                  } catch (UnsatisfiedDependencyException ex) {
                     if (this.logger.isTraceEnabled()) {
                        this.logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
                     }

                     if (causes == null) {
                        causes = new ArrayDeque<>(1);
                     }

                     causes.add(ex);
                     continue;
                  }
               }

               int typeDiffWeight = mbd.isLenientConstructorResolution()
                  ? argsHolder.getTypeDifferenceWeight(paramTypes)
                  : argsHolder.getAssignabilityWeight(paramTypes);
               if (typeDiffWeight < minTypeDiffWeight) {
                  factoryMethodToUse = candidate;
                  argsHolderToUse = argsHolder;
                  argsToUse = argsHolder.arguments;
                  minTypeDiffWeight = typeDiffWeight;
                  ambiguousFactoryMethods = null;
               } else if (factoryMethodToUse != null
                  && typeDiffWeight == minTypeDiffWeight
                  && !mbd.isLenientConstructorResolution()
                  && paramTypes.length == factoryMethodToUse.getParameterCount()
                  && !Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
                  if (ambiguousFactoryMethods == null) {
                     ambiguousFactoryMethods = new LinkedHashSet<>();
                     ambiguousFactoryMethods.add(factoryMethodToUse);
                  }

                  ambiguousFactoryMethods.add(candidate);
               }
            }
         }

         if (factoryMethodToUse == null || argsToUse == null) {
            if (causes != null) {
               UnsatisfiedDependencyException ex = causes.removeLast();

               for (Exception cause : causes) {
                  this.beanFactory.onSuppressedException(cause);
               }

               throw ex;
            }

            List<String> argTypes = new ArrayList<>(minNrOfArgs);
            if (explicitArgs != null) {
               for (Object arg : explicitArgs) {
                  argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
               }
            } else if (resolvedValues != null) {
               Set<ConstructorArgumentValues.ValueHolder> valueHolders = CollectionUtils.newLinkedHashSet(resolvedValues.getArgumentCount());
               valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
               valueHolders.addAll(resolvedValues.getGenericArgumentValues());

               for (ConstructorArgumentValues.ValueHolder value : valueHolders) {
                  String argType = value.getType() != null
                     ? ClassUtils.getShortName(value.getType())
                     : (value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null");
                  argTypes.add(argType);
               }
            }

            String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
            throw new BeanCreationException(
               mbd.getResourceDescription(),
               beanName,
               "No matching factory method found on class ["
                  + factoryClass.getName()
                  + "]: "
                  + (mbd.getFactoryBeanName() != null ? "factory bean '" + mbd.getFactoryBeanName() + "'; " : "")
                  + "factory method '"
                  + mbd.getFactoryMethodName()
                  + "("
                  + argDesc
                  + ")'. Check that a method with the specified name "
                  + (minNrOfArgs > 0 ? "and arguments " : "")
                  + "exists and that it is "
                  + (isStatic ? "static" : "non-static")
                  + "."
            );
         }

         if (void.class == factoryMethodToUse.getReturnType()) {
            throw new BeanCreationException(
               mbd.getResourceDescription(),
               beanName,
               "Invalid factory method '" + mbd.getFactoryMethodName() + "' on class [" + factoryClass.getName() + "]: needs to have a non-void return type!"
            );
         }

         if (KotlinDetector.isSuspendingFunction(factoryMethodToUse)) {
            throw new BeanCreationException(
               mbd.getResourceDescription(),
               beanName,
               "Invalid factory method '" + mbd.getFactoryMethodName() + "' on class [" + factoryClass.getName() + "]: suspending functions are not supported!"
            );
         }

         if (ambiguousFactoryMethods != null) {
            throw new BeanCreationException(
               mbd.getResourceDescription(),
               beanName,
               "Ambiguous factory method matches found on class ["
                  + factoryClass.getName()
                  + "] (hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): "
                  + ambiguousFactoryMethods
            );
         }

         if (explicitArgs == null && argsHolderToUse != null) {
            mbd.factoryMethodToIntrospect = factoryMethodToUse;
            argsHolderToUse.storeCache(mbd, factoryMethodToUse);
         }
      }

      bw.setBeanInstance(this.instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
      return bw;
   }

   private Object instantiate(String beanName, RootBeanDefinition mbd, @Nullable Object factoryBean, Method factoryMethod, @Nullable Object[] args) {
      try {
         return this.beanFactory.getInstantiationStrategy().instantiate(mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
      } catch (Throwable ex) {
         throw new BeanCreationException(mbd.getResourceDescription(), beanName, ex.getMessage(), ex);
      }
   }

   private int resolveConstructorArguments(
      String beanName, RootBeanDefinition mbd, BeanWrapper bw, ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues
   ) {
      TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
      TypeConverter converter = customConverter != null ? customConverter : bw;
      BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
      int minNrOfArgs = cargs.getArgumentCount();

      for (Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {
         int index = entry.getKey();
         if (index < 0) {
            throw new BeanCreationException(mbd.getResourceDescription(), beanName, "Invalid constructor argument index: " + index);
         }

         if (index + 1 > minNrOfArgs) {
            minNrOfArgs = index + 1;
         }

         ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
         if (valueHolder.isConverted()) {
            resolvedValues.addIndexedArgumentValue(index, valueHolder);
         } else {
            Object resolvedValue = valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
            ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
               resolvedValue, valueHolder.getType(), valueHolder.getName()
            );
            resolvedValueHolder.setSource(valueHolder);
            resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
         }
      }

      for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {
         if (valueHolder.isConverted()) {
            resolvedValues.addGenericArgumentValue(valueHolder);
         } else {
            Object resolvedValue = valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
            ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
               resolvedValue, valueHolder.getType(), valueHolder.getName()
            );
            resolvedValueHolder.setSource(valueHolder);
            resolvedValues.addGenericArgumentValue(resolvedValueHolder);
         }
      }

      return minNrOfArgs;
   }

   private ConstructorResolver.ArgumentsHolder createArgumentArray(
      String beanName,
      RootBeanDefinition mbd,
      @Nullable ConstructorArgumentValues resolvedValues,
      BeanWrapper bw,
      Class<?>[] paramTypes,
      @Nullable String @Nullable [] paramNames,
      Executable executable,
      boolean autowiring,
      boolean fallback
   ) throws UnsatisfiedDependencyException {
      TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
      TypeConverter converter = customConverter != null ? customConverter : bw;
      ConstructorResolver.ArgumentsHolder args = new ConstructorResolver.ArgumentsHolder(paramTypes.length);
      Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
      Set<String> allAutowiredBeanNames = new LinkedHashSet<>(paramTypes.length * 2);

      for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
         Class<?> paramType = paramTypes[paramIndex];
         String paramName = paramNames != null ? paramNames[paramIndex] : "";
         ConstructorArgumentValues.ValueHolder valueHolder = null;
         if (resolvedValues != null) {
            valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
            if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
               valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
            }
         }

         if (valueHolder != null) {
            usedValueHolders.add(valueHolder);
            Object originalValue = valueHolder.getValue();
            Object convertedValue;
            if (valueHolder.isConverted()) {
               convertedValue = valueHolder.getConvertedValue();
               args.preparedArguments[paramIndex] = convertedValue;
            } else {
               MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);

               try {
                  convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
               } catch (TypeMismatchException ex) {
                  throw new UnsatisfiedDependencyException(
                     mbd.getResourceDescription(),
                     beanName,
                     new InjectionPoint(methodParam),
                     "Could not convert argument value of type ["
                        + ObjectUtils.nullSafeClassName(valueHolder.getValue())
                        + "] to required type ["
                        + paramType.getName()
                        + "]: "
                        + ex.getMessage()
                  );
               }

               if (valueHolder.getSource() instanceof ConstructorArgumentValues.ValueHolder constructorValueHolder) {
                  Object sourceValue = constructorValueHolder.getValue();
                  args.resolveNecessary = true;
                  args.preparedArguments[paramIndex] = sourceValue;
               }
            }

            args.arguments[paramIndex] = convertedValue;
            args.rawArguments[paramIndex] = originalValue;
         } else {
            MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
            if (!autowiring) {
               throw new UnsatisfiedDependencyException(
                  mbd.getResourceDescription(),
                  beanName,
                  new InjectionPoint(methodParam),
                  "Ambiguous argument values for parameter of type [" + paramType.getName() + "] - did you specify the correct bean references as arguments?"
               );
            }

            try {
               ConstructorResolver.ConstructorDependencyDescriptor desc = new ConstructorResolver.ConstructorDependencyDescriptor(methodParam, true);
               Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
               Object arg = this.resolveAutowiredArgument(desc, paramType, beanName, autowiredBeanNames, converter, fallback);
               if (arg != null) {
                  this.setShortcutIfPossible(desc, paramType, autowiredBeanNames);
               }

               allAutowiredBeanNames.addAll(autowiredBeanNames);
               args.rawArguments[paramIndex] = arg;
               args.arguments[paramIndex] = arg;
               args.preparedArguments[paramIndex] = desc;
               args.resolveNecessary = true;
            } catch (BeansException ex) {
               throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
            }
         }
      }

      this.registerDependentBeans(executable, beanName, allAutowiredBeanNames);
      return args;
   }

   private Object[] resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw, Executable executable, Object[] argsToResolve) {
      TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
      TypeConverter converter = customConverter != null ? customConverter : bw;
      BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
      Class<?>[] paramTypes = executable.getParameterTypes();
      Object[] resolvedArgs = new Object[argsToResolve.length];

      for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
         Object argValue = argsToResolve[argIndex];
         Class<?> paramType = paramTypes[argIndex];
         boolean convertNecessary = false;
         if (argValue instanceof ConstructorResolver.ConstructorDependencyDescriptor descriptor) {
            try {
               argValue = this.resolveAutowiredArgument(descriptor, paramType, beanName, null, converter, true);
            } catch (BeansException ex) {
               Set<String> autowiredBeanNames = null;
               if (descriptor.hasShortcut()) {
                  descriptor.setShortcut(null);
                  autowiredBeanNames = new LinkedHashSet<>(2);
               }

               this.logger.debug("Failed to resolve cached argument", ex);
               argValue = this.resolveAutowiredArgument(descriptor, paramType, beanName, autowiredBeanNames, converter, true);
               if (autowiredBeanNames != null && !descriptor.hasShortcut()) {
                  if (argValue != null) {
                     this.setShortcutIfPossible(descriptor, paramType, autowiredBeanNames);
                  }

                  this.registerDependentBeans(executable, beanName, autowiredBeanNames);
               }
            }
         } else if (argValue instanceof BeanMetadataElement) {
            argValue = valueResolver.resolveValueIfNecessary("constructor argument", argValue);
            convertNecessary = true;
         } else if (argValue instanceof String text) {
            argValue = this.beanFactory.evaluateBeanDefinitionString(text, mbd);
            convertNecessary = true;
         }

         if (convertNecessary) {
            MethodParameter methodParam = MethodParameter.forExecutable(executable, argIndex);

            try {
               argValue = converter.convertIfNecessary(argValue, paramType, methodParam);
            } catch (TypeMismatchException ex) {
               throw new UnsatisfiedDependencyException(
                  mbd.getResourceDescription(),
                  beanName,
                  new InjectionPoint(methodParam),
                  "Could not convert argument value of type ["
                     + ObjectUtils.nullSafeClassName(argValue)
                     + "] to required type ["
                     + paramType.getName()
                     + "]: "
                     + ex.getMessage()
               );
            }
         }

         resolvedArgs[argIndex] = argValue;
      }

      return resolvedArgs;
   }

   private Constructor<?> getUserDeclaredConstructor(Constructor<?> constructor) {
      Class<?> declaringClass = constructor.getDeclaringClass();
      Class<?> userClass = ClassUtils.getUserClass(declaringClass);
      if (userClass != declaringClass) {
         try {
            return userClass.getDeclaredConstructor(constructor.getParameterTypes());
         } catch (NoSuchMethodException var5) {
         }
      }

      return constructor;
   }

   @Nullable Object resolveAutowiredArgument(
      DependencyDescriptor descriptor,
      Class<?> paramType,
      String beanName,
      @Nullable Set<String> autowiredBeanNames,
      TypeConverter typeConverter,
      boolean fallback
   ) {
      if (InjectionPoint.class.isAssignableFrom(paramType)) {
         InjectionPoint injectionPoint = (InjectionPoint)currentInjectionPoint.get();
         if (injectionPoint == null) {
            throw new IllegalStateException("No current InjectionPoint available for " + descriptor);
         } else {
            return injectionPoint;
         }
      } else {
         try {
            return this.beanFactory.resolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
         } catch (NoUniqueBeanDefinitionException ex) {
            throw ex;
         } catch (NoSuchBeanDefinitionException ex) {
            if (fallback) {
               if (paramType.isArray()) {
                  return Array.newInstance(paramType.componentType(), 0);
               }

               if (CollectionFactory.isApproximableCollectionType(paramType)) {
                  return CollectionFactory.createCollection(paramType, 0);
               }

               if (CollectionFactory.isApproximableMapType(paramType)) {
                  return CollectionFactory.createMap(paramType, 0);
               }
            }

            throw ex;
         }
      }
   }

   private void setShortcutIfPossible(ConstructorResolver.ConstructorDependencyDescriptor descriptor, Class<?> paramType, Set<String> autowiredBeanNames) {
      if (autowiredBeanNames.size() == 1) {
         String autowiredBeanName = autowiredBeanNames.iterator().next();
         if (this.beanFactory.containsBean(autowiredBeanName) && this.beanFactory.isTypeMatch(autowiredBeanName, paramType)) {
            descriptor.setShortcut(autowiredBeanName);
         }
      }
   }

   private void registerDependentBeans(Executable executable, String beanName, Set<String> autowiredBeanNames) {
      for (String autowiredBeanName : autowiredBeanNames) {
         this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
         if (this.logger.isDebugEnabled()) {
            this.logger
               .debug(
                  "Autowiring by type from bean name '"
                     + beanName
                     + "' via "
                     + (executable instanceof Constructor ? "constructor" : "factory method")
                     + " to bean named '"
                     + autowiredBeanName
                     + "'"
               );
         }
      }
   }

   public Executable resolveConstructorOrFactoryMethod(String beanName, RootBeanDefinition mbd) {
      Supplier<ResolvableType> beanType = () -> this.getBeanType(beanName, mbd);
      List<ResolvableType> valueTypes = mbd.hasConstructorArgumentValues() ? this.determineParameterValueTypes(mbd) : Collections.emptyList();
      Method resolvedFactoryMethod = this.resolveFactoryMethod(beanName, mbd, valueTypes);
      if (resolvedFactoryMethod != null) {
         return resolvedFactoryMethod;
      }

      Class<?> factoryBeanClass = this.getFactoryBeanClass(beanName, mbd);
      if (factoryBeanClass != null && !factoryBeanClass.equals(mbd.getResolvableType().toClass())) {
         ResolvableType resolvableType = mbd.getResolvableType();
         boolean isCompatible = ResolvableType.forClass(factoryBeanClass).as(FactoryBean.class).getGeneric(new int[]{0}).isAssignableFrom(resolvableType);
         Assert.state(
            isCompatible,
            () -> String.format("Incompatible target type '%s' for factory bean '%s'", resolvableType.toClass().getName(), factoryBeanClass.getName())
         );
         Constructor<?> constructor = this.resolveConstructor(beanName, mbd, () -> ResolvableType.forClass(factoryBeanClass), valueTypes);
         if (constructor != null) {
            return constructor;
         } else {
            throw new IllegalStateException("No suitable FactoryBean constructor found for " + mbd + " and argument types " + valueTypes);
         }
      } else {
         Constructor<?> constructor = this.resolveConstructor(beanName, mbd, beanType, valueTypes);
         if (constructor != null) {
            return constructor;
         } else {
            throw new IllegalStateException("No constructor or factory method candidate found for " + mbd + " and argument types " + valueTypes);
         }
      }
   }

   private List<ResolvableType> determineParameterValueTypes(RootBeanDefinition mbd) {
      List<ResolvableType> parameterTypes = new ArrayList<>();

      for (ConstructorArgumentValues.ValueHolder valueHolder : mbd.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
         parameterTypes.add(this.determineParameterValueType(mbd, valueHolder));
      }

      for (ConstructorArgumentValues.ValueHolder valueHolder : mbd.getConstructorArgumentValues().getGenericArgumentValues()) {
         parameterTypes.add(this.determineParameterValueType(mbd, valueHolder));
      }

      return parameterTypes;
   }

   private ResolvableType determineParameterValueType(RootBeanDefinition mbd, ConstructorArgumentValues.ValueHolder valueHolder) {
      if (valueHolder.getType() != null) {
         return ResolvableType.forClass(ClassUtils.resolveClassName(valueHolder.getType(), this.beanFactory.getBeanClassLoader()));
      } else {
         Object value = valueHolder.getValue();
         if (value instanceof BeanReference br) {
            return value instanceof RuntimeBeanReference rbr && rbr.getBeanType() != null
               ? ResolvableType.forClass(rbr.getBeanType())
               : ResolvableType.forClass(this.beanFactory.getType(br.getBeanName(), false));
         } else if (value instanceof BeanDefinition innerBd) {
            String nameToUse = "(inner bean)";
            ResolvableType type = this.getBeanType(nameToUse, this.beanFactory.getMergedBeanDefinition(nameToUse, innerBd, mbd));
            return FactoryBean.class.isAssignableFrom(type.toClass()) ? type.as(FactoryBean.class).getGeneric(new int[]{0}) : type;
         } else if (value instanceof TypedStringValue typedValue) {
            return typedValue.hasTargetType() ? ResolvableType.forClass(typedValue.getTargetType()) : ResolvableType.forClass(String.class);
         } else {
            return value instanceof Class<?> clazz ? ResolvableType.forClassWithGenerics(Class.class, new Class[]{clazz}) : ResolvableType.forInstance(value);
         }
      }
   }

   private @Nullable Constructor<?> resolveConstructor(
      String beanName, RootBeanDefinition mbd, Supplier<ResolvableType> beanType, List<ResolvableType> valueTypes
   ) {
      Class<?> type = ClassUtils.getUserClass(beanType.get().toClass());
      Constructor<?>[] ctors = this.beanFactory.determineConstructorsFromBeanPostProcessors(type, beanName);
      if (ctors == null) {
         if (!mbd.hasConstructorArgumentValues()) {
            ctors = mbd.getPreferredConstructors();
         }

         if (ctors == null) {
            ctors = mbd.isNonPublicAccessAllowed() ? type.getDeclaredConstructors() : type.getConstructors();
         }
      }

      if (ctors.length == 1) {
         return ctors[0];
      }

      Function<Constructor<?>, List<ResolvableType>> parameterTypesFactory = executable -> {
         List<ResolvableType> types = new ArrayList<>();

         for (int i = 0; i < executable.getParameterCount(); i++) {
            types.add(ResolvableType.forConstructorParameter(executable, i));
         }

         return types;
      };
      List<Constructor<?>> matches = Arrays.stream(ctors)
         .filter(executable -> this.match(parameterTypesFactory.apply((Constructor<?>)executable), valueTypes, ConstructorResolver.FallbackMode.NONE))
         .toList();
      if (matches.size() == 1) {
         return matches.get(0);
      }

      List<Constructor<?>> assignableElementFallbackMatches = Arrays.stream(ctors)
         .filter(
            executable -> this.match(parameterTypesFactory.apply((Constructor<?>)executable), valueTypes, ConstructorResolver.FallbackMode.ASSIGNABLE_ELEMENT)
         )
         .toList();
      if (assignableElementFallbackMatches.size() == 1) {
         return assignableElementFallbackMatches.get(0);
      }

      List<Constructor<?>> typeConversionFallbackMatches = Arrays.stream(ctors)
         .filter(
            executable -> this.match(parameterTypesFactory.apply((Constructor<?>)executable), valueTypes, ConstructorResolver.FallbackMode.TYPE_CONVERSION)
         )
         .toList();
      return typeConversionFallbackMatches.size() == 1 ? typeConversionFallbackMatches.get(0) : null;
   }

   private @Nullable Method resolveFactoryMethod(String beanName, RootBeanDefinition mbd, List<ResolvableType> valueTypes) {
      if (mbd.isFactoryMethodUnique) {
         Method resolvedFactoryMethod = mbd.getResolvedFactoryMethod();
         if (resolvedFactoryMethod != null) {
            return resolvedFactoryMethod;
         }
      }

      String factoryMethodName = mbd.getFactoryMethodName();
      if (factoryMethodName == null) {
         return null;
      }

      String factoryBeanName = mbd.getFactoryBeanName();
      Class<?> factoryClass;
      boolean isStatic;
      if (factoryBeanName != null) {
         factoryClass = this.beanFactory.getType(factoryBeanName);
         isStatic = false;
      } else {
         factoryClass = this.beanFactory.resolveBeanClass(mbd, beanName);
         isStatic = true;
      }

      Assert.state(factoryClass != null, () -> "Failed to determine bean class of " + mbd);
      Method[] rawCandidates = this.getCandidateMethods(factoryClass, mbd);
      List<Method> candidates = new ArrayList<>();

      for (Method candidate : rawCandidates) {
         if ((!isStatic || this.isStaticCandidate(candidate, factoryClass)) && mbd.isFactoryMethod(candidate)) {
            candidates.add(candidate);
         }
      }

      Method result = null;
      if (candidates.size() == 1) {
         result = candidates.get(0);
      } else if (candidates.size() > 1) {
         Function<Method, List<ResolvableType>> parameterTypesFactory = method -> {
            List<ResolvableType> types = new ArrayList<>();

            for (int i = 0; i < method.getParameterCount(); i++) {
               types.add(ResolvableType.forMethodParameter(method, i));
            }

            return types;
         };
         result = this.resolveFactoryMethod(candidates, parameterTypesFactory, valueTypes);
      }

      if (result == null) {
         throw new BeanCreationException(
            mbd.getResourceDescription(),
            beanName,
            "No matching factory method found on class ["
               + factoryClass.getName()
               + "]: "
               + (mbd.getFactoryBeanName() != null ? "factory bean '" + mbd.getFactoryBeanName() + "'; " : "")
               + "factory method '"
               + mbd.getFactoryMethodName()
               + "'. "
         );
      } else {
         return result;
      }
   }

   private @Nullable Method resolveFactoryMethod(
      List<Method> executables, Function<Method, List<ResolvableType>> parameterTypesFactory, List<ResolvableType> valueTypes
   ) {
      List<Method> matches = executables.stream()
         .filter(executable -> this.match(parameterTypesFactory.apply(executable), valueTypes, ConstructorResolver.FallbackMode.NONE))
         .toList();
      if (matches.size() == 1) {
         return matches.get(0);
      }

      List<Method> assignableElementFallbackMatches = executables.stream()
         .filter(executable -> this.match(parameterTypesFactory.apply(executable), valueTypes, ConstructorResolver.FallbackMode.ASSIGNABLE_ELEMENT))
         .toList();
      if (assignableElementFallbackMatches.size() == 1) {
         return assignableElementFallbackMatches.get(0);
      }

      List<Method> typeConversionFallbackMatches = executables.stream()
         .filter(executable -> this.match(parameterTypesFactory.apply(executable), valueTypes, ConstructorResolver.FallbackMode.TYPE_CONVERSION))
         .toList();
      Assert.state(typeConversionFallbackMatches.size() <= 1, () -> "Multiple matches with parameters '" + valueTypes + "': " + typeConversionFallbackMatches);
      return typeConversionFallbackMatches.size() == 1 ? typeConversionFallbackMatches.get(0) : null;
   }

   private boolean match(List<ResolvableType> parameterTypes, List<ResolvableType> valueTypes, ConstructorResolver.FallbackMode fallbackMode) {
      if (parameterTypes.size() != valueTypes.size()) {
         return false;
      }

      for (int i = 0; i < parameterTypes.size(); i++) {
         if (!this.isMatch(parameterTypes.get(i), valueTypes.get(i), fallbackMode)) {
            return false;
         }
      }

      return true;
   }

   private boolean isMatch(ResolvableType parameterType, ResolvableType valueType, ConstructorResolver.FallbackMode fallbackMode) {
      if (this.isAssignable(valueType).test(parameterType)) {
         return true;
      }

      return switch (fallbackMode) {
         case ASSIGNABLE_ELEMENT -> this.isAssignable(valueType).test(this.extractElementType(parameterType));
         case TYPE_CONVERSION -> this.typeConversionFallback(valueType).test(parameterType);
         default -> false;
      };
   }

   private Predicate<ResolvableType> isAssignable(ResolvableType valueType) {
      return parameterType -> valueType == ResolvableType.NONE || parameterType.isAssignableFrom(valueType);
   }

   private ResolvableType extractElementType(ResolvableType parameterType) {
      if (parameterType.isArray()) {
         return parameterType.getComponentType();
      } else {
         return Collection.class.isAssignableFrom(parameterType.toClass()) ? parameterType.as(Collection.class).getGeneric(new int[]{0}) : ResolvableType.NONE;
      }
   }

   private Predicate<ResolvableType> typeConversionFallback(ResolvableType valueType) {
      return parameterType -> this.valueOrCollection(valueType, this::isStringForClassFallback).test(parameterType)
         ? true
         : this.valueOrCollection(valueType, this::isSimpleValueType).test(parameterType);
   }

   private Predicate<ResolvableType> valueOrCollection(ResolvableType valueType, Function<ResolvableType, Predicate<ResolvableType>> predicateProvider) {
      return parameterType -> {
         if (predicateProvider.apply(valueType).test(parameterType)) {
            return true;
         } else {
            return predicateProvider.apply(this.extractElementType(valueType)).test(this.extractElementType(parameterType))
               ? true
               : predicateProvider.apply(valueType).test(this.extractElementType(parameterType));
         }
      };
   }

   private Predicate<ResolvableType> isStringForClassFallback(ResolvableType valueType) {
      return parameterType -> valueType.isAssignableFrom(String.class) && parameterType.isAssignableFrom(Class.class);
   }

   private Predicate<ResolvableType> isSimpleValueType(ResolvableType valueType) {
      return parameterType -> BeanUtils.isSimpleValueType(parameterType.toClass()) && BeanUtils.isSimpleValueType(valueType.toClass());
   }

   private @Nullable Class<?> getFactoryBeanClass(String beanName, RootBeanDefinition mbd) {
      Class<?> beanClass = this.beanFactory.resolveBeanClass(mbd, beanName);
      return beanClass != null && FactoryBean.class.isAssignableFrom(beanClass) ? beanClass : null;
   }

   private ResolvableType getBeanType(String beanName, RootBeanDefinition mbd) {
      ResolvableType resolvableType = mbd.getResolvableType();
      return resolvableType != ResolvableType.NONE ? resolvableType : ResolvableType.forClass(this.beanFactory.resolveBeanClass(mbd, beanName));
   }

   static InjectionPoint setCurrentInjectionPoint(@Nullable InjectionPoint injectionPoint) {
      InjectionPoint old = (InjectionPoint)currentInjectionPoint.get();
      if (injectionPoint != null) {
         currentInjectionPoint.set(injectionPoint);
      } else {
         currentInjectionPoint.remove();
      }

      return old;
   }

   static Constructor<?> @Nullable [] determinePreferredConstructors(Class<?> clazz) {
      Constructor<?> primaryCtor = BeanUtils.findPrimaryConstructor(clazz);

      Constructor<?> defaultCtor;
      try {
         defaultCtor = clazz.getDeclaredConstructor();
      } catch (NoSuchMethodException ex) {
         defaultCtor = null;
      }

      if (primaryCtor != null) {
         return defaultCtor != null && !primaryCtor.equals(defaultCtor) ? new Constructor[]{primaryCtor, defaultCtor} : new Constructor[]{primaryCtor};
      }

      Constructor<?>[] ctors = clazz.getConstructors();
      if (ctors.length == 1) {
         return defaultCtor != null && !ctors[0].equals(defaultCtor) ? new Constructor[]{ctors[0], defaultCtor} : ctors;
      }

      if (ctors.length == 0) {
         ctors = clazz.getDeclaredConstructors();
         if (ctors.length == 1) {
            return ctors;
         }
      }

      return null;
   }

   private static class ArgumentsHolder {
      public final @Nullable Object[] rawArguments;
      public final @Nullable Object[] arguments;
      public final @Nullable Object[] preparedArguments;
      public boolean resolveNecessary = false;

      public ArgumentsHolder(int size) {
         this.rawArguments = new Object[size];
         this.arguments = new Object[size];
         this.preparedArguments = new Object[size];
      }

      public ArgumentsHolder(@Nullable Object[] args) {
         this.rawArguments = args;
         this.arguments = args;
         this.preparedArguments = args;
      }

      public int getTypeDifferenceWeight(Class<?>[] paramTypes) {
         int typeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.arguments);
         int rawTypeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.rawArguments) - 1024;
         return Math.min(rawTypeDiffWeight, typeDiffWeight);
      }

      public int getAssignabilityWeight(Class<?>[] paramTypes) {
         for (int i = 0; i < paramTypes.length; i++) {
            if (!ClassUtils.isAssignableValue(paramTypes[i], this.arguments[i])) {
               return Integer.MAX_VALUE;
            }
         }

         for (int i = 0; i < paramTypes.length; i++) {
            if (!ClassUtils.isAssignableValue(paramTypes[i], this.rawArguments[i])) {
               return 2147483135;
            }
         }

         return 2147482623;
      }

      public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
         synchronized (mbd.constructorArgumentLock) {
            mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;
            mbd.constructorArgumentsResolved = true;
            if (this.resolveNecessary) {
               mbd.preparedConstructorArguments = this.preparedArguments;
            } else {
               mbd.resolvedConstructorArguments = this.arguments;
            }
         }
      }
   }

   private static class ConstructorDependencyDescriptor extends DependencyDescriptor {
      private volatile @Nullable String shortcut;

      public ConstructorDependencyDescriptor(MethodParameter methodParameter, boolean required) {
         super(methodParameter, required);
      }

      public void setShortcut(@Nullable String shortcut) {
         this.shortcut = shortcut;
      }

      public boolean hasShortcut() {
         return this.shortcut != null;
      }

      @Override
      public @Nullable Object resolveShortcut(BeanFactory beanFactory) {
         String shortcut = this.shortcut;
         return shortcut != null ? beanFactory.getBean(shortcut, this.getDependencyType()) : null;
      }

      @Override
      public boolean usesStandardBeanLookup() {
         return true;
      }
   }

   private static class ConstructorPropertiesChecker {
      public static String @Nullable [] evaluate(Constructor<?> candidate, int paramCount) {
         ConstructorProperties cp = candidate.getAnnotation(ConstructorProperties.class);
         if (cp != null) {
            String[] names = cp.value();
            if (names.length != paramCount) {
               throw new IllegalStateException(
                  "Constructor annotated with @ConstructorProperties but not corresponding to actual number of parameters (" + paramCount + "): " + candidate
               );
            } else {
               return names;
            }
         } else {
            return null;
         }
      }
   }

   private enum FallbackMode {
      NONE,
      ASSIGNABLE_ELEMENT,
      TYPE_CONVERSION;
   }
}
