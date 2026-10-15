package org.springframework.resilience.retry;

import java.lang.reflect.Method;

public class SimpleRetryInterceptor extends AbstractRetryInterceptor {
   private final MethodRetrySpec retrySpec;

   public SimpleRetryInterceptor(MethodRetrySpec retrySpec) {
      this.retrySpec = retrySpec;
   }

   @Override
   protected MethodRetrySpec getRetrySpec(Method method, Class<?> targetClass) {
      return this.retrySpec;
   }
}
