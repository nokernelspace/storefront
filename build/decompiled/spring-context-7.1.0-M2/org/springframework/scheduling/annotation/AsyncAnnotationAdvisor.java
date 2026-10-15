package org.springframework.scheduling.annotation;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.function.SingletonSupplier;

public class AsyncAnnotationAdvisor extends AbstractPointcutAdvisor implements BeanFactoryAware {
   private final Advice advice;
   private Pointcut pointcut;

   public AsyncAnnotationAdvisor() {
      this((Supplier<? extends Executor>)null, (Supplier<? extends AsyncUncaughtExceptionHandler>)null);
   }

   public AsyncAnnotationAdvisor(@Nullable Executor executor, @Nullable AsyncUncaughtExceptionHandler exceptionHandler) {
      this(SingletonSupplier.ofNullable(executor), SingletonSupplier.ofNullable(exceptionHandler));
   }

   public AsyncAnnotationAdvisor(
      @Nullable Supplier<? extends @Nullable Executor> executor, @Nullable Supplier<? extends @Nullable AsyncUncaughtExceptionHandler> exceptionHandler
   ) {
      Set<Class<? extends Annotation>> asyncAnnotationTypes = CollectionUtils.newLinkedHashSet(2);
      asyncAnnotationTypes.add(Async.class);
      ClassLoader classLoader = AsyncAnnotationAdvisor.class.getClassLoader();

      try {
         asyncAnnotationTypes.add(ClassUtils.forName("jakarta.ejb.Asynchronous", classLoader));
      } catch (ClassNotFoundException var7) {
      }

      try {
         asyncAnnotationTypes.add(ClassUtils.forName("jakarta.enterprise.concurrent.Asynchronous", classLoader));
      } catch (ClassNotFoundException var6) {
      }

      this.advice = this.buildAdvice(executor, exceptionHandler);
      this.pointcut = this.buildPointcut(asyncAnnotationTypes);
   }

   public void setAsyncAnnotationType(Class<? extends Annotation> asyncAnnotationType) {
      Assert.notNull(asyncAnnotationType, "'asyncAnnotationType' must not be null");
      Set<Class<? extends Annotation>> asyncAnnotationTypes = new HashSet<>();
      asyncAnnotationTypes.add(asyncAnnotationType);
      this.pointcut = this.buildPointcut(asyncAnnotationTypes);
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (this.advice instanceof BeanFactoryAware beanFactoryAware) {
         beanFactoryAware.setBeanFactory(beanFactory);
      }
   }

   public Advice getAdvice() {
      return this.advice;
   }

   public Pointcut getPointcut() {
      return this.pointcut;
   }

   protected Advice buildAdvice(
      @Nullable Supplier<? extends @Nullable Executor> executor, @Nullable Supplier<? extends @Nullable AsyncUncaughtExceptionHandler> exceptionHandler
   ) {
      AnnotationAsyncExecutionInterceptor interceptor = new AnnotationAsyncExecutionInterceptor(null);
      interceptor.configure(executor, exceptionHandler);
      return interceptor;
   }

   protected Pointcut buildPointcut(Set<Class<? extends Annotation>> asyncAnnotationTypes) {
      ComposablePointcut result = null;

      for (Class<? extends Annotation> asyncAnnotationType : asyncAnnotationTypes) {
         Pointcut cpc = new AnnotationMatchingPointcut(asyncAnnotationType, true);
         Pointcut mpc = new AnnotationMatchingPointcut(null, asyncAnnotationType, true);
         if (result == null) {
            result = new ComposablePointcut(cpc);
         } else {
            result.union(cpc);
         }

         result = result.union(mpc);
      }

      return (Pointcut)(result != null ? result : Pointcut.TRUE);
   }
}
