package org.springframework.cache.interceptor;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CacheOperationInvoker {
   @Nullable Object invoke() throws CacheOperationInvoker.ThrowableWrapper;

   class ThrowableWrapper extends RuntimeException {
      private final Throwable original;

      public ThrowableWrapper(Throwable original) {
         super(original.getMessage(), original);
         this.original = original;
      }

      public Throwable getOriginal() {
         return this.original;
      }
   }
}
