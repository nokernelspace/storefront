package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

class CacheExpressionRootObject {
   private final Collection<? extends Cache> caches;
   private final Method method;
   private final @Nullable Object[] args;
   private final Object target;
   private final Class<?> targetClass;

   public CacheExpressionRootObject(Collection<? extends Cache> caches, Method method, @Nullable Object[] args, Object target, Class<?> targetClass) {
      this.method = method;
      this.target = target;
      this.targetClass = targetClass;
      this.args = args;
      this.caches = caches;
   }

   public Collection<? extends Cache> getCaches() {
      return this.caches;
   }

   public Method getMethod() {
      return this.method;
   }

   public String getMethodName() {
      return this.method.getName();
   }

   public @Nullable Object[] getArgs() {
      return this.args;
   }

   public Object getTarget() {
      return this.target;
   }

   public Class<?> getTargetClass() {
      return this.targetClass;
   }
}
