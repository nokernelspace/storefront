package org.springframework.cache.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class CacheInterceptor extends CacheAspectSupport implements MethodInterceptor, Serializable {
   public @Nullable Object invoke(final MethodInvocation invocation) throws Throwable {
      Method method = invocation.getMethod();
      CacheOperationInvoker aopAllianceInvoker = () -> {
         try {
            return invocation.proceed();
         } catch (Throwable ex) {
            throw new CacheOperationInvoker.ThrowableWrapper(ex);
         }
      };
      Object target = invocation.getThis();
      Assert.state(target != null, "Target must not be null");

      try {
         return this.execute(aopAllianceInvoker, target, method, invocation.getArguments());
      } catch (CacheOperationInvoker.ThrowableWrapper th) {
         throw th.getOriginal();
      }
   }
}
