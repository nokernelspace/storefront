package org.springframework.scheduling.annotation;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.core.annotation.AnnotatedElementUtils;

public class AnnotationAsyncExecutionInterceptor extends AsyncExecutionInterceptor {
   public AnnotationAsyncExecutionInterceptor(@Nullable Executor defaultExecutor) {
      super(defaultExecutor);
   }

   public AnnotationAsyncExecutionInterceptor(@Nullable Executor defaultExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
      super(defaultExecutor, exceptionHandler);
   }

   protected @Nullable String getExecutorQualifier(Method method) {
      Async async = (Async)AnnotatedElementUtils.findMergedAnnotation(method, Async.class);
      if (async == null) {
         async = (Async)AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Async.class);
      }

      return async != null ? async.value() : null;
   }
}
