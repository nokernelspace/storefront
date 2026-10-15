package org.springframework.aop.framework;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NamedThreadLocal;

public final class AopContext {
   private static final ThreadLocal<Object> currentProxy = new NamedThreadLocal("Current AOP proxy");

   private AopContext() {
   }

   public static Object currentProxy() throws IllegalStateException {
      Object proxy = currentProxy.get();
      if (proxy == null) {
         throw new IllegalStateException(
            "Cannot find current proxy: Set 'exposeProxy' property on Advised to 'true' to make it available, and ensure that AopContext.currentProxy() is invoked in the same thread as the AOP invocation context."
         );
      } else {
         return proxy;
      }
   }

   static @Nullable Object setCurrentProxy(@Nullable Object proxy) {
      Object old = currentProxy.get();
      if (proxy != null) {
         currentProxy.set(proxy);
      } else {
         currentProxy.remove();
      }

      return old;
   }
}
