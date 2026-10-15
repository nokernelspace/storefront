package org.springframework.context.annotation;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;

public class ContextAnnotationAutowireCandidateResolver extends QualifierAnnotationAutowireCandidateResolver {
   public @Nullable Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
      return this.isLazy(descriptor) ? this.buildLazyResolutionProxy(descriptor, beanName) : null;
   }

   public @Nullable Class<?> getLazyResolutionProxyClass(DependencyDescriptor descriptor, @Nullable String beanName) {
      return this.isLazy(descriptor) ? (Class)this.buildLazyResolutionProxy(descriptor, beanName, true) : null;
   }

   protected boolean isLazy(DependencyDescriptor descriptor) {
      for (Annotation ann : descriptor.getAnnotations()) {
         if (ann instanceof Lazy lazy && lazy.value()) {
            return true;
         }

         Lazy lazy = (Lazy)AnnotationUtils.findAnnotation(ann.annotationType(), Lazy.class);
         if (lazy != null && lazy.value()) {
            return true;
         }
      }

      MethodParameter methodParam = descriptor.getMethodParameter();
      if (methodParam != null) {
         Method method = methodParam.getMethod();
         if (method == null || void.class == method.getReturnType()) {
            Lazy lazy = (Lazy)AnnotationUtils.findAnnotation(methodParam.getAnnotatedElement(), Lazy.class);
            if (lazy != null && lazy.value()) {
               return true;
            }
         }
      }

      return false;
   }

   protected Object buildLazyResolutionProxy(DependencyDescriptor descriptor, @Nullable String beanName) {
      return this.buildLazyResolutionProxy(descriptor, beanName, false);
   }

   private Object buildLazyResolutionProxy(DependencyDescriptor descriptor, @Nullable String beanName, boolean classOnly) {
      if (this.getBeanFactory() instanceof DefaultListableBeanFactory dlbf) {
         ContextAnnotationAutowireCandidateResolver.LazyDependencyTargetSource var9 = new ContextAnnotationAutowireCandidateResolver.LazyDependencyTargetSource(
            dlbf, descriptor, beanName
         );
         ProxyFactory pf = new ProxyFactory();
         pf.setTargetSource(var9);
         Class<?> dependencyType = descriptor.getDependencyType();
         if (dependencyType.isInterface()) {
            pf.addInterface(dependencyType);
         }

         ClassLoader classLoader = dlbf.getBeanClassLoader();
         return classOnly ? pf.getProxyClass(classLoader) : pf.getProxy(classLoader);
      } else {
         throw new IllegalStateException("Lazy resolution only supported with DefaultListableBeanFactory");
      }
   }

   private static class LazyDependencyTargetSource implements TargetSource, Serializable {
      private final DefaultListableBeanFactory beanFactory;
      private final DependencyDescriptor descriptor;
      private final @Nullable String beanName;
      private transient volatile @Nullable Object cachedTarget;

      public LazyDependencyTargetSource(DefaultListableBeanFactory beanFactory, DependencyDescriptor descriptor, @Nullable String beanName) {
         this.beanFactory = beanFactory;
         this.descriptor = descriptor;
         this.beanName = beanName;
      }

      public Class<?> getTargetClass() {
         return this.descriptor.getDependencyType();
      }

      public Object getTarget() {
         Object cachedTarget = this.cachedTarget;
         if (cachedTarget != null) {
            return cachedTarget;
         }

         Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
         Object target = this.beanFactory.doResolveDependency(this.descriptor, this.beanName, autowiredBeanNames, null);
         if (target == null) {
            Class<?> type = this.getTargetClass();
            if (Map.class == type) {
               target = Collections.emptyMap();
            } else if (List.class == type) {
               target = Collections.emptyList();
            } else {
               if (Set.class != type && Collection.class != type) {
                  throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType(), "Optional dependency not present for lazy injection point");
               }

               target = Collections.emptySet();
            }
         } else if (target instanceof Map<?, ?> map && Map.class == this.getTargetClass()) {
            target = Collections.unmodifiableMap(map);
         } else if (target instanceof List<?> list && List.class == this.getTargetClass()) {
            target = Collections.unmodifiableList(list);
         } else if (target instanceof Set<?> set && Set.class == this.getTargetClass()) {
            target = Collections.unmodifiableSet(set);
         } else if (target instanceof Collection<?> coll && Collection.class == this.getTargetClass()) {
            target = Collections.unmodifiableCollection(coll);
         }

         boolean cacheable = false;
         if (!autowiredBeanNames.isEmpty()) {
            cacheable = true;

            for (String autowiredBeanName : autowiredBeanNames) {
               if (!this.beanFactory.containsBean(autowiredBeanName)) {
                  cacheable = false;
               } else {
                  if (this.beanName != null) {
                     this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
                  }

                  if (!this.beanFactory.isSingleton(autowiredBeanName)) {
                     cacheable = false;
                  }
               }
            }
         }

         if (cacheable) {
            this.cachedTarget = target;
         }

         return target;
      }
   }
}
