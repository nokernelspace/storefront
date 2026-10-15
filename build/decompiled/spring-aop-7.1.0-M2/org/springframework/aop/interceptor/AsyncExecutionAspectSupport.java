package org.springframework.aop.interceptor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;
import org.springframework.util.function.SingletonSupplier;

public abstract class AsyncExecutionAspectSupport implements BeanFactoryAware {
   public static final String DEFAULT_TASK_EXECUTOR_BEAN_NAME = "taskExecutor";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private SingletonSupplier<Executor> defaultExecutor;
   private SingletonSupplier<AsyncUncaughtExceptionHandler> exceptionHandler;
   private @Nullable BeanFactory beanFactory;
   private @Nullable StringValueResolver embeddedValueResolver;
   private final Map<Method, AsyncTaskExecutor> executors = new ConcurrentHashMap<>(16);

   public AsyncExecutionAspectSupport(@Nullable Executor defaultExecutor) {
      this.defaultExecutor = new SingletonSupplier(defaultExecutor, () -> this.getDefaultExecutor(this.beanFactory));
      this.exceptionHandler = SingletonSupplier.of(SimpleAsyncUncaughtExceptionHandler::new);
   }

   public AsyncExecutionAspectSupport(@Nullable Executor defaultExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
      this.defaultExecutor = new SingletonSupplier(defaultExecutor, () -> this.getDefaultExecutor(this.beanFactory));
      this.exceptionHandler = SingletonSupplier.of(exceptionHandler);
   }

   public void configure(
      @Nullable Supplier<? extends @Nullable Executor> defaultExecutor, @Nullable Supplier<? extends @Nullable AsyncUncaughtExceptionHandler> exceptionHandler
   ) {
      this.defaultExecutor = new SingletonSupplier(defaultExecutor, () -> this.getDefaultExecutor(this.beanFactory));
      this.exceptionHandler = new SingletonSupplier(exceptionHandler, SimpleAsyncUncaughtExceptionHandler::new);
   }

   public void setExecutor(Executor defaultExecutor) {
      this.defaultExecutor = SingletonSupplier.of(defaultExecutor);
   }

   public void setExceptionHandler(AsyncUncaughtExceptionHandler exceptionHandler) {
      this.exceptionHandler = SingletonSupplier.of(exceptionHandler);
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      if (beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
         this.embeddedValueResolver = new EmbeddedValueResolver(configurableBeanFactory);
      }

      this.executors.clear();
   }

   protected @Nullable AsyncTaskExecutor determineAsyncExecutor(Method method) {
      AsyncTaskExecutor executor = this.executors.get(method);
      if (executor == null) {
         String qualifier = this.getExecutorQualifier(method);
         if (this.embeddedValueResolver != null && StringUtils.hasLength(qualifier)) {
            qualifier = this.embeddedValueResolver.resolveStringValue(qualifier);
         }

         Executor targetExecutor;
         if (StringUtils.hasLength(qualifier)) {
            targetExecutor = this.findQualifiedExecutor(this.beanFactory, qualifier);
         } else {
            targetExecutor = (Executor)this.defaultExecutor.get();
         }

         if (targetExecutor == null) {
            return null;
         }

         executor = (AsyncTaskExecutor)(targetExecutor instanceof AsyncTaskExecutor asyncTaskExecutor
            ? asyncTaskExecutor
            : new TaskExecutorAdapter(targetExecutor));
         this.executors.put(method, executor);
      }

      return executor;
   }

   protected abstract @Nullable String getExecutorQualifier(Method method);

   protected @Nullable Executor findQualifiedExecutor(@Nullable BeanFactory beanFactory, String qualifier) {
      if (beanFactory == null) {
         throw new IllegalStateException("BeanFactory must be set on " + this.getClass().getSimpleName() + " to access qualified executor '" + qualifier + "'");
      } else {
         return (Executor)BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, Executor.class, qualifier);
      }
   }

   protected @Nullable Executor getDefaultExecutor(@Nullable BeanFactory beanFactory) {
      if (beanFactory != null) {
         try {
            return (Executor)beanFactory.getBean(TaskExecutor.class);
         } catch (NoUniqueBeanDefinitionException ex) {
            this.logger.debug("Could not find unique TaskExecutor bean. Continuing search for an Executor bean named 'taskExecutor'", ex);

            try {
               return (Executor)beanFactory.getBean("taskExecutor", Executor.class);
            } catch (NoSuchBeanDefinitionException ex2) {
               if (this.logger.isInfoEnabled()) {
                  this.logger
                     .info(
                        "More than one TaskExecutor bean found within the context, and none is named 'taskExecutor'. Mark one of them as primary or name it 'taskExecutor' (possibly as an alias) in order to use it for async processing: "
                           + ex.getBeanNamesFound()
                     );
               }
            }
         } catch (NoSuchBeanDefinitionException ex) {
            this.logger.debug("Could not find default TaskExecutor bean. Continuing search for an Executor bean named 'taskExecutor'", ex);

            try {
               return (Executor)beanFactory.getBean("taskExecutor", Executor.class);
            } catch (NoSuchBeanDefinitionException ex2) {
               this.logger.info("No task executor bean found for async processing: no bean of type TaskExecutor and no bean named 'taskExecutor' either");
            }
         }
      }

      return null;
   }

   protected @Nullable Object doSubmit(Callable<Object> task, AsyncTaskExecutor executor, Class<?> returnType) {
      if (CompletableFuture.class.isAssignableFrom(returnType)) {
         return executor.submitCompletable(task);
      }

      if (Future.class.isAssignableFrom(returnType)) {
         return executor.submit(task);
      }

      if (void.class != returnType && !"kotlin.Unit".equals(returnType.getName())) {
         throw new IllegalArgumentException("Invalid return type for async method (only Future and void supported): " + returnType);
      }

      executor.submit(task);
      return null;
   }

   protected void handleError(Throwable ex, Method method, @Nullable Object... params) throws Exception {
      if (Future.class.isAssignableFrom(method.getReturnType())) {
         ReflectionUtils.rethrowException(ex);
      } else {
         try {
            ((AsyncUncaughtExceptionHandler)this.exceptionHandler.obtain()).handleUncaughtException(ex, method, params);
         } catch (Throwable ex2) {
            this.logger.warn("Exception handler for async method '" + method.toGenericString() + "' threw unexpected exception itself", ex2);
         }
      }
   }
}
