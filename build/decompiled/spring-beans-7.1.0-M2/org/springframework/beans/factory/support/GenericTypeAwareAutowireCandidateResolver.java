package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

public class GenericTypeAwareAutowireCandidateResolver extends SimpleAutowireCandidateResolver implements BeanFactoryAware, Cloneable {
   private @Nullable BeanFactory beanFactory;

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   protected final @Nullable BeanFactory getBeanFactory() {
      return this.beanFactory;
   }

   @Override
   public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
      return !super.isAutowireCandidate(bdHolder, descriptor) ? false : this.checkGenericTypeMatch(bdHolder, descriptor);
   }

   protected boolean checkGenericTypeMatch(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
      ResolvableType dependencyType = descriptor.getResolvableType();
      if (dependencyType.getType() instanceof Class) {
         return true;
      }

      ResolvableType targetType = null;
      boolean cacheType = false;
      RootBeanDefinition rbd = null;
      if (bdHolder.getBeanDefinition() instanceof RootBeanDefinition rootBeanDef) {
         rbd = rootBeanDef;
      }

      if (rbd != null) {
         targetType = rbd.targetType;
         if (targetType == null) {
            cacheType = true;
            targetType = this.getReturnTypeForFactoryMethod(rbd, descriptor);
            if (targetType == null) {
               RootBeanDefinition dbd = this.getResolvedDecoratedDefinition(rbd);
               if (dbd != null) {
                  targetType = dbd.targetType;
                  if (targetType == null) {
                     targetType = this.getReturnTypeForFactoryMethod(dbd, descriptor);
                  }
               }
            }
         }
      }

      if (targetType == null) {
         if (this.beanFactory != null) {
            Class<?> beanType = this.beanFactory.getType(bdHolder.getBeanName());
            if (beanType != null) {
               targetType = ResolvableType.forClass(ClassUtils.getUserClass(beanType));
            }
         }

         if (targetType == null && rbd != null && rbd.hasBeanClass() && rbd.getFactoryMethodName() == null) {
            Class<?> beanClass = rbd.getBeanClass();
            if (!FactoryBean.class.isAssignableFrom(beanClass)) {
               targetType = ResolvableType.forClass(ClassUtils.getUserClass(beanClass));
            }
         }
      }

      if (targetType == null) {
         return true;
      }

      if (cacheType) {
         rbd.targetType = targetType;
      }

      Class<?> targetClass = targetType.resolve();
      if (targetClass != null && FactoryBean.class.isAssignableFrom(targetClass)) {
         Class<?> classToMatch = dependencyType.resolve();
         if (classToMatch != null && !FactoryBean.class.isAssignableFrom(classToMatch) && !classToMatch.isAssignableFrom(targetClass)) {
            targetType = targetType.getGeneric(new int[0]);
            if (descriptor.fallbackMatchAllowed()) {
               targetType = ResolvableType.forClass(targetType.resolve());
            }
         }
      }

      if (descriptor.fallbackMatchAllowed()) {
         if (targetType.hasUnresolvableGenerics()) {
            return dependencyType.isAssignableFromResolvedPart(targetType);
         }

         if (targetType.resolve() == Properties.class) {
            return true;
         }
      }

      return dependencyType.isAssignableFrom(targetType);
   }

   protected @Nullable RootBeanDefinition getResolvedDecoratedDefinition(RootBeanDefinition rbd) {
      BeanDefinitionHolder decDef = rbd.getDecoratedDefinition();
      return decDef != null
            && this.beanFactory instanceof ConfigurableListableBeanFactory clbf
            && clbf.containsBeanDefinition(decDef.getBeanName())
            && clbf.getMergedBeanDefinition(decDef.getBeanName()) instanceof RootBeanDefinition rootBeanDef
         ? rootBeanDef
         : null;
   }

   protected @Nullable ResolvableType getReturnTypeForFactoryMethod(RootBeanDefinition rbd, DependencyDescriptor descriptor) {
      ResolvableType returnType = rbd.factoryMethodReturnType;
      if (returnType == null) {
         Method factoryMethod = rbd.getResolvedFactoryMethod();
         if (factoryMethod != null) {
            returnType = ResolvableType.forMethodReturnType(factoryMethod);
         }
      }

      if (returnType != null) {
         Class<?> resolvedClass = returnType.resolve();
         if (resolvedClass != null && descriptor.getDependencyType().isAssignableFrom(resolvedClass)) {
            return returnType;
         }
      }

      return null;
   }

   @Override
   public AutowireCandidateResolver cloneIfNecessary() {
      try {
         return (AutowireCandidateResolver)this.clone();
      } catch (CloneNotSupportedException ex) {
         throw new IllegalStateException(ex);
      }
   }
}
