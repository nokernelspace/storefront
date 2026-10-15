package org.springframework.scheduling.annotation;

import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

@Deprecated(since = "6.0")
public class AsyncConfigurerSupport implements AsyncConfigurer {
   @Override
   public @Nullable Executor getAsyncExecutor() {
      return null;
   }

   @Override
   public @Nullable AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
      return null;
   }
}
