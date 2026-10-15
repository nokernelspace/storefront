package org.springframework.resilience.retry;

import java.lang.reflect.Method;
import java.util.function.Predicate;

@FunctionalInterface
public interface MethodRetryPredicate {
   boolean shouldRetry(Method method, Throwable throwable);

   default Predicate<Throwable> forMethod(Method method) {
      return t -> this.shouldRetry(method, t);
   }
}
