package org.springframework.beans.factory.annotation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

public class InitDestroyAnnotationBeanPostProcessor
   implements DestructionAwareBeanPostProcessor,
   MergedBeanDefinitionPostProcessor,
   BeanRegistrationAotProcessor,
   PriorityOrdered,
   Serializable {
   private final transient InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata emptyLifecycleMetadata = new InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata(
      Object.class, Collections.emptyList(), Collections.emptyList()
   ) {
      @Override
      public void checkInitDestroyMethods(RootBeanDefinition beanDefinition) {
      }

      @Override
      public void invokeInitMethods(Object target, String beanName) {
      }

      @Override
      public void invokeDestroyMethods(Object target, String beanName) {
      }

      @Override
      public boolean hasDestroyMethods() {
         return false;
      }
   };
   protected transient Log logger = LogFactory.getLog(this.getClass());
   private final Set<Class<? extends Annotation>> initAnnotationTypes = new LinkedHashSet<>(2);
   private final Set<Class<? extends Annotation>> destroyAnnotationTypes = new LinkedHashSet<>(2);
   private int order = Integer.MAX_VALUE;
   private final transient @Nullable Map<Class<?>, InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata> lifecycleMetadataCache = new ConcurrentHashMap<>(
      256
   );

   public void setInitAnnotationType(Class<? extends Annotation> initAnnotationType) {
      this.initAnnotationTypes.clear();
      this.initAnnotationTypes.add(initAnnotationType);
   }

   public void addInitAnnotationType(@Nullable Class<? extends Annotation> initAnnotationType) {
      if (initAnnotationType != null) {
         this.initAnnotationTypes.add(initAnnotationType);
      }
   }

   public void setDestroyAnnotationType(Class<? extends Annotation> destroyAnnotationType) {
      this.destroyAnnotationTypes.clear();
      this.destroyAnnotationTypes.add(destroyAnnotationType);
   }

   public void addDestroyAnnotationType(@Nullable Class<? extends Annotation> destroyAnnotationType) {
      if (destroyAnnotationType != null) {
         this.destroyAnnotationTypes.add(destroyAnnotationType);
      }
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanClass, String beanName) {
      this.findLifecycleMetadata(beanDefinition, beanClass);
   }

   @Override
   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      RootBeanDefinition beanDefinition = registeredBean.getMergedBeanDefinition();
      beanDefinition.resolveDestroyMethodIfNecessary();
      InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata metadata = this.findLifecycleMetadata(beanDefinition, registeredBean.getBeanClass());
      if (!CollectionUtils.isEmpty(metadata.initMethods)) {
         String[] initMethodNames = safeMerge(beanDefinition.getInitMethodNames(), metadata.initMethods);
         beanDefinition.setInitMethodNames(initMethodNames);
      }

      if (!CollectionUtils.isEmpty(metadata.destroyMethods)) {
         String[] destroyMethodNames = safeMerge(beanDefinition.getDestroyMethodNames(), metadata.destroyMethods);
         beanDefinition.setDestroyMethodNames(destroyMethodNames);
      }

      return null;
   }

   private InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata findLifecycleMetadata(RootBeanDefinition beanDefinition, Class<?> beanClass) {
      InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata metadata = this.findLifecycleMetadata(beanClass);
      metadata.checkInitDestroyMethods(beanDefinition);
      return metadata;
   }

   private static String[] safeMerge(String @Nullable [] existingNames, Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> detectedMethods) {
      Stream<String> detectedNames = detectedMethods.stream().map(InitDestroyAnnotationBeanPostProcessor.LifecycleMethod::getIdentifier);
      Stream<String> mergedNames = existingNames != null ? Stream.concat(detectedNames, Stream.of(existingNames)) : detectedNames;
      return mergedNames.distinct().toArray(String[]::new);
   }

   @Override
   public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata metadata = this.findLifecycleMetadata(bean.getClass());

      try {
         metadata.invokeInitMethods(bean, beanName);
         return bean;
      } catch (InvocationTargetException ex) {
         throw new BeanCreationException(beanName, "Invocation of init method failed", ex.getTargetException());
      } catch (Throwable ex) {
         throw new BeanCreationException(beanName, "Failed to invoke init method", ex);
      }
   }

   @Override
   public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
      return bean;
   }

   @Override
   public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
      InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata metadata = this.findLifecycleMetadata(bean.getClass());

      try {
         metadata.invokeDestroyMethods(bean, beanName);
      } catch (InvocationTargetException ex) {
         String msg = "Destroy method on bean with name '" + beanName + "' threw an exception";
         if (this.logger.isDebugEnabled()) {
            this.logger.warn(msg, ex.getTargetException());
         } else if (this.logger.isWarnEnabled()) {
            this.logger.warn(msg + ": " + ex.getTargetException());
         }
      } catch (Throwable ex) {
         if (this.logger.isWarnEnabled()) {
            this.logger.warn("Failed to invoke destroy method on bean with name '" + beanName + "'", ex);
         }
      }
   }

   @Override
   public boolean requiresDestruction(Object bean) {
      return this.findLifecycleMetadata(bean.getClass()).hasDestroyMethods();
   }

   private InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata findLifecycleMetadata(Class<?> beanClass) {
      if (this.lifecycleMetadataCache == null) {
         return this.buildLifecycleMetadata(beanClass);
      }

      InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata metadata = this.lifecycleMetadataCache.get(beanClass);
      if (metadata == null) {
         synchronized (this.lifecycleMetadataCache) {
            metadata = this.lifecycleMetadataCache.get(beanClass);
            if (metadata == null) {
               metadata = this.buildLifecycleMetadata(beanClass);
               this.lifecycleMetadataCache.put(beanClass, metadata);
            }

            return metadata;
         }
      } else {
         return metadata;
      }
   }

   private InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata buildLifecycleMetadata(final Class<?> beanClass) {
      if (!AnnotationUtils.isCandidateClass(beanClass, this.initAnnotationTypes) && !AnnotationUtils.isCandidateClass(beanClass, this.destroyAnnotationTypes)) {
         return this.emptyLifecycleMetadata;
      }

      List<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> initMethods = new ArrayList<>();
      List<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> destroyMethods = new ArrayList<>();
      Class<?> currentClass = beanClass;

      do {
         List<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> currInitMethods = new ArrayList<>();
         List<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> currDestroyMethods = new ArrayList<>();
         ReflectionUtils.doWithLocalMethods(currentClass, method -> {
            for (Class<? extends Annotation> initAnnotationType : this.initAnnotationTypes) {
               if (initAnnotationType != null && method.isAnnotationPresent(initAnnotationType)) {
                  currInitMethods.add(new InitDestroyAnnotationBeanPostProcessor.LifecycleMethod(method, beanClass));
                  if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Found init method on class [" + beanClass.getName() + "]: " + method);
                  }
               }
            }

            for (Class<? extends Annotation> destroyAnnotationType : this.destroyAnnotationTypes) {
               if (destroyAnnotationType != null && method.isAnnotationPresent(destroyAnnotationType)) {
                  currDestroyMethods.add(new InitDestroyAnnotationBeanPostProcessor.LifecycleMethod(method, beanClass));
                  if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Found destroy method on class [" + beanClass.getName() + "]: " + method);
                  }
               }
            }
         });
         initMethods.addAll(0, currInitMethods);
         destroyMethods.addAll(currDestroyMethods);
         currentClass = currentClass.getSuperclass();
      } while (currentClass != null && currentClass != Object.class);

      return initMethods.isEmpty() && destroyMethods.isEmpty()
         ? this.emptyLifecycleMetadata
         : new InitDestroyAnnotationBeanPostProcessor.LifecycleMetadata(beanClass, initMethods, destroyMethods);
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.logger = LogFactory.getLog(this.getClass());
   }

   private class LifecycleMetadata {
      private final Class<?> beanClass;
      private final Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> initMethods;
      private final Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> destroyMethods;
      private volatile @Nullable Set<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedInitMethods;
      private volatile @Nullable Set<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedDestroyMethods;

      public LifecycleMetadata(
         Class<?> beanClass,
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> initMethods,
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> destroyMethods
      ) {
         this.beanClass = beanClass;
         this.initMethods = initMethods;
         this.destroyMethods = destroyMethods;
      }

      public void checkInitDestroyMethods(RootBeanDefinition beanDefinition) {
         Set<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedInitMethods = CollectionUtils.newLinkedHashSet(this.initMethods.size());

         for (InitDestroyAnnotationBeanPostProcessor.LifecycleMethod lifecycleMethod : this.initMethods) {
            String methodIdentifier = lifecycleMethod.getIdentifier();
            if (!beanDefinition.isExternallyManagedInitMethod(methodIdentifier)) {
               beanDefinition.registerExternallyManagedInitMethod(methodIdentifier);
               checkedInitMethods.add(lifecycleMethod);
               if (InitDestroyAnnotationBeanPostProcessor.this.logger.isTraceEnabled()) {
                  InitDestroyAnnotationBeanPostProcessor.this.logger
                     .trace("Registered init method on class [" + this.beanClass.getName() + "]: " + methodIdentifier);
               }
            }
         }

         Set<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedDestroyMethods = CollectionUtils.newLinkedHashSet(this.destroyMethods.size());

         for (InitDestroyAnnotationBeanPostProcessor.LifecycleMethod lifecycleMethod : this.destroyMethods) {
            String methodIdentifier = lifecycleMethod.getIdentifier();
            if (!beanDefinition.isExternallyManagedDestroyMethod(methodIdentifier)) {
               beanDefinition.registerExternallyManagedDestroyMethod(methodIdentifier);
               checkedDestroyMethods.add(lifecycleMethod);
               if (InitDestroyAnnotationBeanPostProcessor.this.logger.isTraceEnabled()) {
                  InitDestroyAnnotationBeanPostProcessor.this.logger
                     .trace("Registered destroy method on class [" + this.beanClass.getName() + "]: " + methodIdentifier);
               }
            }
         }

         this.checkedInitMethods = checkedInitMethods;
         this.checkedDestroyMethods = checkedDestroyMethods;
      }

      public void invokeInitMethods(Object target, String beanName) throws Throwable {
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedInitMethods = this.checkedInitMethods;
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> initMethodsToIterate = checkedInitMethods != null
            ? checkedInitMethods
            : this.initMethods;
         if (!initMethodsToIterate.isEmpty()) {
            for (InitDestroyAnnotationBeanPostProcessor.LifecycleMethod lifecycleMethod : initMethodsToIterate) {
               if (InitDestroyAnnotationBeanPostProcessor.this.logger.isTraceEnabled()) {
                  InitDestroyAnnotationBeanPostProcessor.this.logger.trace("Invoking init method on bean '" + beanName + "': " + lifecycleMethod.getMethod());
               }

               lifecycleMethod.invoke(target);
            }
         }
      }

      public void invokeDestroyMethods(Object target, String beanName) throws Throwable {
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedDestroyMethods = this.checkedDestroyMethods;
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> destroyMethodsToUse = checkedDestroyMethods != null
            ? checkedDestroyMethods
            : this.destroyMethods;
         if (!destroyMethodsToUse.isEmpty()) {
            for (InitDestroyAnnotationBeanPostProcessor.LifecycleMethod lifecycleMethod : destroyMethodsToUse) {
               if (InitDestroyAnnotationBeanPostProcessor.this.logger.isTraceEnabled()) {
                  InitDestroyAnnotationBeanPostProcessor.this.logger
                     .trace("Invoking destroy method on bean '" + beanName + "': " + lifecycleMethod.getMethod());
               }

               lifecycleMethod.invoke(target);
            }
         }
      }

      public boolean hasDestroyMethods() {
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> checkedDestroyMethods = this.checkedDestroyMethods;
         Collection<InitDestroyAnnotationBeanPostProcessor.LifecycleMethod> destroyMethodsToUse = checkedDestroyMethods != null
            ? checkedDestroyMethods
            : this.destroyMethods;
         return !destroyMethodsToUse.isEmpty();
      }
   }

   private static class LifecycleMethod {
      private final Method method;
      private final String identifier;

      public LifecycleMethod(Method method, Class<?> beanClass) {
         if (method.getParameterCount() != 0) {
            throw new IllegalStateException("Lifecycle annotation requires a no-arg method: " + method);
         }

         this.method = method;
         this.identifier = isPrivateOrNotVisible(method, beanClass) ? ClassUtils.getQualifiedMethodName(method) : method.getName();
      }

      public Method getMethod() {
         return this.method;
      }

      public String getIdentifier() {
         return this.identifier;
      }

      public void invoke(Object target) throws Throwable {
         ReflectionUtils.makeAccessible(this.method);
         this.method.invoke(target);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof InitDestroyAnnotationBeanPostProcessor.LifecycleMethod that && this.identifier.equals(that.identifier);
      }

      @Override
      public int hashCode() {
         return this.identifier.hashCode();
      }

      private static boolean isPrivateOrNotVisible(Method method, Class<?> beanClass) {
         int modifiers = method.getModifiers();
         return Modifier.isPrivate(modifiers)
            ? true
            : !method.getDeclaringClass().getPackageName().equals(beanClass.getPackageName())
               && !Modifier.isPublic(modifiers)
               && !Modifier.isProtected(modifiers);
      }
   }
}
