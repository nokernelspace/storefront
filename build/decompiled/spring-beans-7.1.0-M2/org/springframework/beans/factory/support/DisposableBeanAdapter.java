package org.springframework.beans.factory.support;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

class DisposableBeanAdapter implements DisposableBean, Runnable, Serializable {
   private static final String DESTROY_METHOD_NAME = "destroy";
   private static final String CLOSE_METHOD_NAME = "close";
   private static final String SHUTDOWN_METHOD_NAME = "shutdown";
   private static final Log logger = LogFactory.getLog(DisposableBeanAdapter.class);
   private static final boolean REACTIVE_STREAMS_PRESENT = ClassUtils.isPresent("org.reactivestreams.Publisher", DisposableBeanAdapter.class.getClassLoader());
   private final Object bean;
   private final String beanName;
   private final boolean nonPublicAccessAllowed;
   private final boolean invokeDisposableBean;
   private boolean invokeAutoCloseable;
   private String @Nullable [] destroyMethodNames;
   private transient Method @Nullable [] destroyMethods;
   private final @Nullable List<DestructionAwareBeanPostProcessor> beanPostProcessors;

   public DisposableBeanAdapter(Object bean, String beanName, RootBeanDefinition beanDefinition, List<DestructionAwareBeanPostProcessor> postProcessors) {
      Assert.notNull(bean, "Disposable bean must not be null");
      this.bean = bean;
      this.beanName = beanName;
      this.nonPublicAccessAllowed = beanDefinition.isNonPublicAccessAllowed();
      this.invokeDisposableBean = bean instanceof DisposableBean && !beanDefinition.hasAnyExternallyManagedDestroyMethod("destroy");
      String[] destroyMethodNames = inferDestroyMethodsIfNecessary(bean.getClass(), beanDefinition);
      if (!ObjectUtils.isEmpty(destroyMethodNames)
         && (!this.invokeDisposableBean || !"destroy".equals(destroyMethodNames[0]))
         && !beanDefinition.hasAnyExternallyManagedDestroyMethod(destroyMethodNames[0])) {
         this.invokeAutoCloseable = bean instanceof AutoCloseable && "close".equals(destroyMethodNames[0]);
         if (!this.invokeAutoCloseable) {
            this.destroyMethodNames = destroyMethodNames;
            List<Method> destroyMethods = new ArrayList<>(destroyMethodNames.length);

            for (String destroyMethodName : destroyMethodNames) {
               Method destroyMethod = this.determineDestroyMethod(destroyMethodName);
               if (destroyMethod == null) {
                  if (beanDefinition.isEnforceDestroyMethod()) {
                     throw new BeanDefinitionValidationException(
                        "Could not find a destroy method named '" + destroyMethodName + "' on bean with name '" + beanName + "'"
                     );
                  }
               } else {
                  if (destroyMethod.getParameterCount() > 0) {
                     Class<?>[] paramTypes = destroyMethod.getParameterTypes();
                     if (paramTypes.length > 1) {
                        throw new BeanDefinitionValidationException(
                           "Method '" + destroyMethodName + "' of bean '" + beanName + "' has more than one parameter - not supported as destroy method"
                        );
                     }

                     if (paramTypes.length == 1 && boolean.class != paramTypes[0]) {
                        throw new BeanDefinitionValidationException(
                           "Method '" + destroyMethodName + "' of bean '" + beanName + "' has a non-boolean parameter - not supported as destroy method"
                        );
                     }
                  }

                  destroyMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(destroyMethod, bean.getClass());
                  destroyMethods.add(destroyMethod);
               }
            }

            this.destroyMethods = destroyMethods.toArray(Method[]::new);
         }
      }

      this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
   }

   public DisposableBeanAdapter(Object bean, List<DestructionAwareBeanPostProcessor> postProcessors) {
      Assert.notNull(bean, "Disposable bean must not be null");
      this.bean = bean;
      this.beanName = bean.getClass().getName();
      this.nonPublicAccessAllowed = true;
      this.invokeDisposableBean = this.bean instanceof DisposableBean;
      this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
   }

   private DisposableBeanAdapter(
      Object bean,
      String beanName,
      boolean nonPublicAccessAllowed,
      boolean invokeDisposableBean,
      boolean invokeAutoCloseable,
      String @Nullable [] destroyMethodNames,
      @Nullable List<DestructionAwareBeanPostProcessor> postProcessors
   ) {
      this.bean = bean;
      this.beanName = beanName;
      this.nonPublicAccessAllowed = nonPublicAccessAllowed;
      this.invokeDisposableBean = invokeDisposableBean;
      this.invokeAutoCloseable = invokeAutoCloseable;
      this.destroyMethodNames = destroyMethodNames;
      this.beanPostProcessors = postProcessors;
   }

   @Override
   public void run() {
      this.destroy();
   }

   @Override
   public void destroy() {
      if (!CollectionUtils.isEmpty(this.beanPostProcessors)) {
         for (DestructionAwareBeanPostProcessor processor : this.beanPostProcessors) {
            processor.postProcessBeforeDestruction(this.bean, this.beanName);
         }
      }

      if (this.invokeDisposableBean) {
         if (logger.isTraceEnabled()) {
            logger.trace("Invoking destroy() on bean with name '" + this.beanName + "'");
         }

         try {
            ((DisposableBean)this.bean).destroy();
         } catch (Throwable ex) {
            if (logger.isWarnEnabled()) {
               String msg = "Invocation of destroy method failed on bean with name '" + this.beanName + "'";
               if (logger.isDebugEnabled()) {
                  logger.warn(msg, ex);
               } else {
                  logger.warn(msg + ": " + ex);
               }
            }
         }
      }

      if (this.invokeAutoCloseable) {
         if (logger.isTraceEnabled()) {
            logger.trace("Invoking close() on bean with name '" + this.beanName + "'");
         }

         try {
            ((AutoCloseable)this.bean).close();
         } catch (Throwable ex) {
            if (logger.isWarnEnabled()) {
               String msg = "Invocation of close method failed on bean with name '" + this.beanName + "'";
               if (logger.isDebugEnabled()) {
                  logger.warn(msg, ex);
               } else {
                  logger.warn(msg + ": " + ex);
               }
            }
         }
      } else if (this.destroyMethods != null) {
         for (Method destroyMethod : this.destroyMethods) {
            this.invokeCustomDestroyMethod(destroyMethod);
         }
      } else if (this.destroyMethodNames != null) {
         for (String destroyMethodName : this.destroyMethodNames) {
            Method destroyMethod = this.determineDestroyMethod(destroyMethodName);
            if (destroyMethod != null) {
               destroyMethod = ClassUtils.getPubliclyAccessibleMethodIfPossible(destroyMethod, this.bean.getClass());
               this.invokeCustomDestroyMethod(destroyMethod);
            }
         }
      }
   }

   private @Nullable Method determineDestroyMethod(String destroyMethodName) {
      try {
         Class<?> beanClass = this.bean.getClass();
         MethodDescriptor descriptor = MethodDescriptor.create(this.beanName, beanClass, destroyMethodName);
         String methodName = descriptor.methodName();
         Method destroyMethod = this.findDestroyMethod(descriptor.declaringClass(), methodName);
         if (destroyMethod != null) {
            return destroyMethod;
         }

         for (Class<?> beanInterface : ClassUtils.getAllInterfacesForClass(beanClass)) {
            destroyMethod = this.findDestroyMethod(beanInterface, methodName);
            if (destroyMethod != null) {
               return destroyMethod;
            }
         }

         return null;
      } catch (IllegalArgumentException ex) {
         throw new BeanDefinitionValidationException("Could not find unique destroy method on bean with name '" + this.beanName + ": " + ex.getMessage());
      }
   }

   private @Nullable Method findDestroyMethod(Class<?> clazz, String name) {
      return this.nonPublicAccessAllowed
         ? BeanUtils.findMethodWithMinimalParameters(clazz, name)
         : BeanUtils.findMethodWithMinimalParameters(clazz.getMethods(), name);
   }

   private void invokeCustomDestroyMethod(Method destroyMethod) {
      if (logger.isTraceEnabled()) {
         logger.trace("Invoking custom destroy method '" + destroyMethod.getName() + "' on bean with name '" + this.beanName + "': " + destroyMethod);
      }

      int paramCount = destroyMethod.getParameterCount();
      Object[] args = new Object[paramCount];
      if (paramCount == 1) {
         args[0] = Boolean.TRUE;
      }

      try {
         ReflectionUtils.makeAccessible(destroyMethod);
         Object returnValue = destroyMethod.invoke(this.bean, args);
         if (returnValue == null) {
            this.logDestroyMethodCompletion(destroyMethod, false);
         } else if (returnValue instanceof Future<?> future) {
            future.get();
            this.logDestroyMethodCompletion(destroyMethod, true);
         } else if ((!REACTIVE_STREAMS_PRESENT || !new DisposableBeanAdapter.ReactiveDestroyMethodHandler().await(destroyMethod, returnValue))
            && logger.isDebugEnabled()) {
            logger.debug(
               "Unknown return value type from custom destroy method '"
                  + destroyMethod.getName()
                  + "' on bean with name '"
                  + this.beanName
                  + "': "
                  + returnValue.getClass()
            );
         }
      } catch (InvocationTargetException | ExecutionException ex) {
         this.logDestroyMethodException(destroyMethod, ex.getCause());
      } catch (Throwable ex) {
         if (logger.isWarnEnabled()) {
            logger.warn("Failed to invoke custom destroy method '" + destroyMethod.getName() + "' on bean with name '" + this.beanName + "'", ex);
         }
      }
   }

   void logDestroyMethodException(Method destroyMethod, @Nullable Throwable ex) {
      if (logger.isWarnEnabled()) {
         String msg = "Custom destroy method '" + destroyMethod.getName() + "' on bean with name '" + this.beanName + "' propagated an exception";
         if (logger.isDebugEnabled()) {
            logger.warn(msg, ex);
         } else {
            logger.warn(msg + ": " + ex);
         }
      }
   }

   void logDestroyMethodCompletion(Method destroyMethod, boolean async) {
      if (logger.isDebugEnabled()) {
         logger.debug(
            "Custom destroy method '" + destroyMethod.getName() + "' on bean with name '" + this.beanName + "' completed" + (async ? " asynchronously" : "")
         );
      }
   }

   protected Object writeReplace() {
      List<DestructionAwareBeanPostProcessor> serializablePostProcessors = null;
      if (this.beanPostProcessors != null) {
         serializablePostProcessors = new ArrayList<>();

         for (DestructionAwareBeanPostProcessor postProcessor : this.beanPostProcessors) {
            if (postProcessor instanceof Serializable) {
               serializablePostProcessors.add(postProcessor);
            }
         }
      }

      return new DisposableBeanAdapter(
         this.bean,
         this.beanName,
         this.nonPublicAccessAllowed,
         this.invokeDisposableBean,
         this.invokeAutoCloseable,
         this.destroyMethodNames,
         serializablePostProcessors
      );
   }

   public static boolean hasDestroyMethod(Object bean, RootBeanDefinition beanDefinition) {
      return bean instanceof DisposableBean || inferDestroyMethodsIfNecessary(bean.getClass(), beanDefinition) != null;
   }

   static String @Nullable [] inferDestroyMethodsIfNecessary(Class<?> target, RootBeanDefinition beanDefinition) {
      String[] destroyMethodNames = beanDefinition.getDestroyMethodNames();
      if (destroyMethodNames != null && destroyMethodNames.length > 1) {
         return destroyMethodNames;
      }

      String destroyMethodName = beanDefinition.resolvedDestroyMethodName;
      if (destroyMethodName == null) {
         destroyMethodName = beanDefinition.getDestroyMethodName();
         boolean autoCloseable = AutoCloseable.class.isAssignableFrom(target);
         boolean executorService = ExecutorService.class.isAssignableFrom(target);
         if ("(inferred)".equals(destroyMethodName) || destroyMethodName == null && (autoCloseable || executorService)) {
            destroyMethodName = null;
            if (!DisposableBean.class.isAssignableFrom(target)) {
               if (executorService) {
                  destroyMethodName = "shutdown";

                  try {
                     if (target.getMethod("close").getDeclaringClass() != ExecutorService.class) {
                        destroyMethodName = "close";
                     }
                  } catch (NoSuchMethodException var10) {
                  }
               } else if (autoCloseable) {
                  destroyMethodName = "close";
               } else {
                  try {
                     destroyMethodName = target.getMethod("close").getName();
                  } catch (NoSuchMethodException ex) {
                     try {
                        destroyMethodName = target.getMethod("shutdown").getName();
                     } catch (NoSuchMethodException var8) {
                     }
                  }
               }
            }
         }

         beanDefinition.resolvedDestroyMethodName = destroyMethodName != null ? destroyMethodName : "";
      }

      return StringUtils.hasLength(destroyMethodName) ? new String[]{destroyMethodName} : null;
   }

   public static boolean hasApplicableProcessors(Object bean, List<DestructionAwareBeanPostProcessor> postProcessors) {
      if (!CollectionUtils.isEmpty(postProcessors)) {
         for (DestructionAwareBeanPostProcessor processor : postProcessors) {
            if (processor.requiresDestruction(bean)) {
               return true;
            }
         }
      }

      return false;
   }

   private static @Nullable List<DestructionAwareBeanPostProcessor> filterPostProcessors(List<DestructionAwareBeanPostProcessor> processors, Object bean) {
      List<DestructionAwareBeanPostProcessor> filteredPostProcessors = null;
      if (!CollectionUtils.isEmpty(processors)) {
         filteredPostProcessors = new ArrayList<>(processors.size());

         for (DestructionAwareBeanPostProcessor processor : processors) {
            if (processor.requiresDestruction(bean)) {
               filteredPostProcessors.add(processor);
            }
         }
      }

      return filteredPostProcessors;
   }

   private class DestroyMethodSubscriber implements Subscriber<Object> {
      private final Method destroyMethod;
      private final CountDownLatch latch;

      public DestroyMethodSubscriber(Method destroyMethod, CountDownLatch latch) {
         this.destroyMethod = destroyMethod;
         this.latch = latch;
      }

      public void onSubscribe(Subscription s) {
         s.request(2147483647L);
      }

      public void onNext(Object o) {
      }

      public void onError(Throwable t) {
         this.latch.countDown();
         DisposableBeanAdapter.this.logDestroyMethodException(this.destroyMethod, t);
      }

      public void onComplete() {
         this.latch.countDown();
         DisposableBeanAdapter.this.logDestroyMethodCompletion(this.destroyMethod, true);
      }
   }

   private class ReactiveDestroyMethodHandler {
      public boolean await(Method destroyMethod, Object returnValue) throws InterruptedException {
         ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(returnValue.getClass());
         if (adapter != null) {
            CountDownLatch latch = new CountDownLatch(1);
            adapter.toPublisher(returnValue).subscribe(DisposableBeanAdapter.this.new DestroyMethodSubscriber(destroyMethod, latch));
            latch.await();
            return true;
         } else {
            return false;
         }
      }
   }
}
