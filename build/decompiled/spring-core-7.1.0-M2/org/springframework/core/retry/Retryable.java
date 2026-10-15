package org.springframework.core.retry;

@FunctionalInterface
public interface Retryable<R> {
   R execute() throws Throwable;

   default String getName() {
      return this.getClass().getName();
   }
}
