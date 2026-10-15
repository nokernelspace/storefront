package org.springframework.scheduling.concurrent;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.ErrorHandler;
import org.springframework.util.ReflectionUtils;

class DelegatingErrorHandlingCallable<V> implements Callable<V> {
   private final Callable<V> delegate;
   private final ErrorHandler errorHandler;

   public DelegatingErrorHandlingCallable(Callable<V> delegate, @Nullable ErrorHandler errorHandler) {
      this.delegate = delegate;
      this.errorHandler = errorHandler != null ? errorHandler : TaskUtils.getDefaultErrorHandler(false);
   }

   @Override
   public @Nullable V call() throws Exception {
      try {
         return this.delegate.call();
      } catch (Throwable var4) {
         Throwable ex = var4;

         try {
            this.errorHandler.handleError(ex);
         } catch (UndeclaredThrowableException exToPropagate) {
            ReflectionUtils.rethrowException(exToPropagate.getUndeclaredThrowable());
         }

         return null;
      }
   }
}
