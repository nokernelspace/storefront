package org.springframework.scheduling.annotation;

import java.lang.annotation.Annotation;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;

public class AsyncAnnotationBeanPostProcessor extends AbstractBeanFactoryAwareAdvisingPostProcessor {
   public static final String DEFAULT_TASK_EXECUTOR_BEAN_NAME = "taskExecutor";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable Supplier<? extends @Nullable Executor> executor;
   private @Nullable Supplier<? extends @Nullable AsyncUncaughtExceptionHandler> exceptionHandler;
   private @Nullable Class<? extends Annotation> asyncAnnotationType;

   public AsyncAnnotationBeanPostProcessor() {
      this.setBeforeExistingAdvisors(true);
   }

   public void configure(
      @Nullable Supplier<? extends @Nullable Executor> executor, @Nullable Supplier<? extends @Nullable AsyncUncaughtExceptionHandler> exceptionHandler
   ) {
      this.executor = executor;
      this.exceptionHandler = exceptionHandler;
   }

   public void setExecutor(Executor executor) {
      this.executor = SingletonSupplier.of(executor);
   }

   public void setExceptionHandler(AsyncUncaughtExceptionHandler exceptionHandler) {
      this.exceptionHandler = SingletonSupplier.of(exceptionHandler);
   }

   public void setAsyncAnnotationType(Class<? extends Annotation> asyncAnnotationType) {
      Assert.notNull(asyncAnnotationType, "'asyncAnnotationType' must not be null");
      this.asyncAnnotationType = asyncAnnotationType;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      super.setBeanFactory(beanFactory);
      AsyncAnnotationAdvisor advisor = new AsyncAnnotationAdvisor(this.executor, this.exceptionHandler);
      if (this.asyncAnnotationType != null) {
         advisor.setAsyncAnnotationType(this.asyncAnnotationType);
      }

      advisor.setBeanFactory(beanFactory);
      this.advisor = advisor;
   }
}
