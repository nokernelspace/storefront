package org.springframework.scheduling.annotation;

import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

public interface AsyncConfigurer {
   default @Nullable Executor getAsyncExecutor() {
      return null;
   }

   default @Nullable AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
      return null;
   }
}
