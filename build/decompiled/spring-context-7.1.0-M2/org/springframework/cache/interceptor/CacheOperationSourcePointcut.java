package org.springframework.cache.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.cache.CacheManager;
import org.springframework.util.ObjectUtils;

final class CacheOperationSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {
   private @Nullable CacheOperationSource cacheOperationSource;

   public CacheOperationSourcePointcut() {
      this.setClassFilter(new CacheOperationSourcePointcut.CacheOperationSourceClassFilter());
   }

   public void setCacheOperationSource(@Nullable CacheOperationSource cacheOperationSource) {
      this.cacheOperationSource = cacheOperationSource;
   }

   public boolean matches(Method method, Class<?> targetClass) {
      return this.cacheOperationSource == null || this.cacheOperationSource.hasCacheOperations(method, targetClass);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof CacheOperationSourcePointcut that && ObjectUtils.nullSafeEquals(this.cacheOperationSource, that.cacheOperationSource);
   }

   @Override
   public int hashCode() {
      return CacheOperationSourcePointcut.class.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.cacheOperationSource;
   }

   private final class CacheOperationSourceClassFilter implements ClassFilter {
      public boolean matches(Class<?> clazz) {
         return CacheManager.class.isAssignableFrom(clazz)
            ? false
            : CacheOperationSourcePointcut.this.cacheOperationSource == null || CacheOperationSourcePointcut.this.cacheOperationSource.isCandidateClass(clazz);
      }

      private @Nullable CacheOperationSource getCacheOperationSource() {
         return CacheOperationSourcePointcut.this.cacheOperationSource;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof CacheOperationSourcePointcut.CacheOperationSourceClassFilter that
               && ObjectUtils.nullSafeEquals(this.getCacheOperationSource(), that.getCacheOperationSource());
      }

      @Override
      public int hashCode() {
         return CacheOperationSourcePointcut.CacheOperationSourceClassFilter.class.hashCode();
      }

      @Override
      public String toString() {
         return CacheOperationSourcePointcut.CacheOperationSourceClassFilter.class.getName() + ": " + this.getCacheOperationSource();
      }
   }
}
