package org.springframework.aop.framework.autoproxy;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NamedThreadLocal;

public final class ProxyCreationContext {
   private static final ThreadLocal<String> currentProxiedBeanName = new NamedThreadLocal("Name of currently proxied bean");

   private ProxyCreationContext() {
   }

   public static @Nullable String getCurrentProxiedBeanName() {
      return currentProxiedBeanName.get();
   }

   static void setCurrentProxiedBeanName(@Nullable String beanName) {
      if (beanName != null) {
         currentProxiedBeanName.set(beanName);
      } else {
         currentProxiedBeanName.remove();
      }
   }
}
