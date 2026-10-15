package org.springframework.beans.factory.support;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

public class RootBeanDefinition extends AbstractBeanDefinition {
   private @Nullable BeanDefinitionHolder decoratedDefinition;
   private @Nullable AnnotatedElement qualifiedElement;
   volatile boolean stale;
   boolean allowCaching = true;
   boolean isFactoryMethodUnique;
   volatile @Nullable ResolvableType targetType;
   volatile @Nullable Class<?> resolvedTargetType;
   volatile @Nullable Boolean isFactoryBean;
   volatile @Nullable ResolvableType factoryMethodReturnType;
   volatile @Nullable Method factoryMethodToIntrospect;
   volatile @Nullable String resolvedDestroyMethodName;
   final Object constructorArgumentLock = new Object();
   @Nullable Executable resolvedConstructorOrFactoryMethod;
   boolean constructorArgumentsResolved = false;
   @Nullable Object @Nullable [] resolvedConstructorArguments;
   @Nullable Object @Nullable [] preparedConstructorArguments;
   final Object postProcessingLock = new Object();
   boolean postProcessed = false;
   volatile @Nullable Boolean beforeInstantiationResolved;
   private @Nullable Set<Member> externallyManagedConfigMembers;
   private @Nullable Set<String> externallyManagedInitMethods;
   private @Nullable Set<String> externallyManagedDestroyMethods;

   public RootBeanDefinition() {
   }

   public RootBeanDefinition(@Nullable Class<?> beanClass) {
      this.setBeanClass(beanClass);
   }

   @Deprecated(since = "6.0.11")
   public RootBeanDefinition(@Nullable ResolvableType beanType) {
      this.setTargetType(beanType);
   }

   public <T> RootBeanDefinition(@Nullable Class<T> beanClass, @Nullable Supplier<T> instanceSupplier) {
      this.setBeanClass(beanClass);
      this.setInstanceSupplier(instanceSupplier);
   }

   public <T> RootBeanDefinition(@Nullable Class<T> beanClass, String scope, @Nullable Supplier<T> instanceSupplier) {
      this.setBeanClass(beanClass);
      this.setScope(scope);
      this.setInstanceSupplier(instanceSupplier);
   }

   public RootBeanDefinition(@Nullable Class<?> beanClass, int autowireMode, boolean dependencyCheck) {
      this.setBeanClass(beanClass);
      this.setAutowireMode(autowireMode);
      if (dependencyCheck && this.getResolvedAutowireMode() != 3) {
         this.setDependencyCheck(1);
      }
   }

   public RootBeanDefinition(@Nullable Class<?> beanClass, @Nullable ConstructorArgumentValues cargs, @Nullable MutablePropertyValues pvs) {
      super(cargs, pvs);
      this.setBeanClass(beanClass);
   }

   public RootBeanDefinition(String beanClassName) {
      this.setBeanClassName(beanClassName);
   }

   public RootBeanDefinition(String beanClassName, ConstructorArgumentValues cargs, MutablePropertyValues pvs) {
      super(cargs, pvs);
      this.setBeanClassName(beanClassName);
   }

   public RootBeanDefinition(RootBeanDefinition original) {
      super(original);
      this.decoratedDefinition = original.decoratedDefinition;
      this.qualifiedElement = original.qualifiedElement;
      this.allowCaching = original.allowCaching;
      this.isFactoryMethodUnique = original.isFactoryMethodUnique;
      this.targetType = original.targetType;
      this.factoryMethodToIntrospect = original.factoryMethodToIntrospect;
   }

   RootBeanDefinition(BeanDefinition original) {
      super(original);
   }

   @Override
   public @Nullable String getParentName() {
      return null;
   }

   @Override
   public void setParentName(@Nullable String parentName) {
      if (parentName != null) {
         throw new IllegalArgumentException("Root bean cannot be changed into a child bean with parent reference");
      }
   }

   public void setDecoratedDefinition(@Nullable BeanDefinitionHolder decoratedDefinition) {
      this.decoratedDefinition = decoratedDefinition;
   }

   public @Nullable BeanDefinitionHolder getDecoratedDefinition() {
      return this.decoratedDefinition;
   }

   public void setQualifiedElement(@Nullable AnnotatedElement qualifiedElement) {
      this.qualifiedElement = qualifiedElement;
   }

   public @Nullable AnnotatedElement getQualifiedElement() {
      return this.qualifiedElement;
   }

   public void setTargetType(@Nullable ResolvableType targetType) {
      this.targetType = targetType;
   }

   public void setTargetType(@Nullable Class<?> targetType) {
      this.targetType = targetType != null ? ResolvableType.forClass(targetType) : null;
   }

   public @Nullable Class<?> getTargetType() {
      if (this.resolvedTargetType != null) {
         return this.resolvedTargetType;
      }

      ResolvableType targetType = this.targetType;
      return targetType != null ? targetType.resolve() : null;
   }

   @Override
   public ResolvableType getResolvableType() {
      ResolvableType targetType = this.targetType;
      if (targetType != null) {
         return targetType;
      }

      ResolvableType returnType = this.factoryMethodReturnType;
      if (returnType != null) {
         return returnType;
      }

      Method factoryMethod = this.getResolvedFactoryMethod();
      return factoryMethod != null ? ResolvableType.forMethodReturnType(factoryMethod) : super.getResolvableType();
   }

   public Constructor<?> @Nullable [] getPreferredConstructors() {
      Object attribute = this.getAttribute("preferredConstructors");
      if (attribute == null) {
         return null;
      } else if (attribute instanceof Constructor<?> constructor) {
         return new Constructor[]{constructor};
      } else if (attribute instanceof Constructor<?>[] constructors) {
         return constructors;
      } else {
         throw new IllegalArgumentException("Invalid value type for attribute 'preferredConstructors': " + attribute.getClass().getName());
      }
   }

   public void setUniqueFactoryMethodName(String name) {
      Assert.hasText(name, "Factory method name must not be empty");
      this.setFactoryMethodName(name);
      this.isFactoryMethodUnique = true;
   }

   public void setNonUniqueFactoryMethodName(String name) {
      Assert.hasText(name, "Factory method name must not be empty");
      this.setFactoryMethodName(name);
      this.isFactoryMethodUnique = false;
   }

   public boolean isFactoryMethod(Method candidate) {
      return candidate.getName().equals(this.getFactoryMethodName());
   }

   public void setResolvedFactoryMethod(@Nullable Method method) {
      this.factoryMethodToIntrospect = method;
      if (method != null) {
         this.setUniqueFactoryMethodName(method.getName());
      }
   }

   public @Nullable Method getResolvedFactoryMethod() {
      Method factoryMethod = this.factoryMethodToIntrospect;
      if (factoryMethod == null && this.getInstanceSupplier() instanceof InstanceSupplier<?> instanceSupplier) {
         factoryMethod = instanceSupplier.getFactoryMethod();
      }

      return factoryMethod;
   }

   public void markAsPostProcessed() {
      synchronized (this.postProcessingLock) {
         this.postProcessed = true;
      }
   }

   public void registerExternallyManagedConfigMember(Member configMember) {
      synchronized (this.postProcessingLock) {
         if (this.externallyManagedConfigMembers == null) {
            this.externallyManagedConfigMembers = new LinkedHashSet<>(1);
         }

         this.externallyManagedConfigMembers.add(configMember);
      }
   }

   public boolean isExternallyManagedConfigMember(Member configMember) {
      synchronized (this.postProcessingLock) {
         return this.externallyManagedConfigMembers != null && this.externallyManagedConfigMembers.contains(configMember);
      }
   }

   public Set<Member> getExternallyManagedConfigMembers() {
      synchronized (this.postProcessingLock) {
         return this.externallyManagedConfigMembers != null
            ? Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedConfigMembers))
            : Collections.emptySet();
      }
   }

   public void registerExternallyManagedInitMethod(String initMethod) {
      synchronized (this.postProcessingLock) {
         if (this.externallyManagedInitMethods == null) {
            this.externallyManagedInitMethods = new LinkedHashSet<>(1);
         }

         this.externallyManagedInitMethods.add(initMethod);
      }
   }

   public boolean isExternallyManagedInitMethod(String initMethod) {
      synchronized (this.postProcessingLock) {
         return this.externallyManagedInitMethods != null && this.externallyManagedInitMethods.contains(initMethod);
      }
   }

   boolean hasAnyExternallyManagedInitMethod(String initMethod) {
      synchronized (this.postProcessingLock) {
         return this.isExternallyManagedInitMethod(initMethod) ? true : hasAnyExternallyManagedMethod(this.externallyManagedInitMethods, initMethod);
      }
   }

   public Set<String> getExternallyManagedInitMethods() {
      synchronized (this.postProcessingLock) {
         return this.externallyManagedInitMethods != null
            ? Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedInitMethods))
            : Collections.emptySet();
      }
   }

   public void resolveDestroyMethodIfNecessary() {
      this.setDestroyMethodNames(DisposableBeanAdapter.inferDestroyMethodsIfNecessary(this.getResolvableType().toClass(), this));
   }

   public void registerExternallyManagedDestroyMethod(String destroyMethod) {
      synchronized (this.postProcessingLock) {
         if (this.externallyManagedDestroyMethods == null) {
            this.externallyManagedDestroyMethods = new LinkedHashSet<>(1);
         }

         this.externallyManagedDestroyMethods.add(destroyMethod);
      }
   }

   public boolean isExternallyManagedDestroyMethod(String destroyMethod) {
      synchronized (this.postProcessingLock) {
         return this.externallyManagedDestroyMethods != null && this.externallyManagedDestroyMethods.contains(destroyMethod);
      }
   }

   boolean hasAnyExternallyManagedDestroyMethod(String destroyMethod) {
      synchronized (this.postProcessingLock) {
         return this.isExternallyManagedDestroyMethod(destroyMethod)
            ? true
            : hasAnyExternallyManagedMethod(this.externallyManagedDestroyMethods, destroyMethod);
      }
   }

   private static boolean hasAnyExternallyManagedMethod(@Nullable Set<String> candidates, String methodName) {
      if (candidates != null) {
         for (String candidate : candidates) {
            int indexOfDot = candidate.lastIndexOf(46);
            if (indexOfDot > 0) {
               String candidateMethodName = candidate.substring(indexOfDot + 1);
               if (candidateMethodName.equals(methodName)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public Set<String> getExternallyManagedDestroyMethods() {
      synchronized (this.postProcessingLock) {
         return this.externallyManagedDestroyMethods != null
            ? Collections.unmodifiableSet(new LinkedHashSet<>(this.externallyManagedDestroyMethods))
            : Collections.emptySet();
      }
   }

   public RootBeanDefinition cloneBeanDefinition() {
      return new RootBeanDefinition(this);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof RootBeanDefinition && super.equals(other);
   }

   @Override
   public String toString() {
      return "Root bean: " + super.toString();
   }
}
