package org.springframework.aop.interceptor;

import java.io.Serializable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ConcurrencyThrottleSupport;

public class ConcurrencyThrottleInterceptor extends ConcurrencyThrottleSupport implements MethodInterceptor, Serializable {
   public ConcurrencyThrottleInterceptor() {
      this(1);
   }

   public ConcurrencyThrottleInterceptor(int concurrencyLimit) {
      this.setConcurrencyLimit(concurrencyLimit);
   }

   @Override
   public @Nullable Object invoke(MethodInvocation methodInvocation) throws Throwable {
      this.beforeAccess();

      try {
         return methodInvocation.proceed();
      } finally {
         this.afterAccess();
      }
   }
}
