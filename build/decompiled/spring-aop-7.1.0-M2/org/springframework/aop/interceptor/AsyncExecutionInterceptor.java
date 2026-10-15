package org.springframework.aop.interceptor;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

public class AsyncExecutionInterceptor extends AsyncExecutionAspectSupport implements MethodInterceptor, Ordered {
   public AsyncExecutionInterceptor(@Nullable Executor defaultExecutor) {
      super(defaultExecutor);
   }

   public AsyncExecutionInterceptor(@Nullable Executor defaultExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
      super(defaultExecutor, exceptionHandler);
   }

   @Override
   public @Nullable Object invoke(final MethodInvocation invocation) throws Throwable {
      Class<?> targetClass = invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null;
      Method userMethod = BridgeMethodResolver.getMostSpecificMethod(invocation.getMethod(), targetClass);
      AsyncTaskExecutor executor = this.determineAsyncExecutor(userMethod);
      if (executor == null) {
         throw new IllegalStateException("No executor specified and no default executor set on AsyncExecutionInterceptor either");
      }

      Callable<Object> task = () -> {
         try {
            if (invocation.proceed() instanceof Future<?> future) {
               return future.get();
            }
         } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            this.handleError(cause == null ? ex : cause, userMethod, invocation.getArguments());
         } catch (Throwable ex) {
            this.handleError(ex, userMethod, invocation.getArguments());
         }

         return null;
      };
      return this.doSubmit(task, executor, userMethod.getReturnType());
   }

   @Override
   protected @Nullable String getExecutorQualifier(Method method) {
      return null;
   }

   @Override
   protected @Nullable Executor getDefaultExecutor(@Nullable BeanFactory beanFactory) {
      Executor defaultExecutor = super.getDefaultExecutor(beanFactory);
      return (Executor)(defaultExecutor != null ? defaultExecutor : new SimpleAsyncTaskExecutor());
   }

   public int getOrder() {
      return Integer.MIN_VALUE;
   }
}
