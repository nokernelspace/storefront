package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.MethodClassKey;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

public abstract class AbstractFallbackCacheOperationSource implements CacheOperationSource {
   private static final Collection<CacheOperation> NULL_CACHING_MARKER = Collections.emptyList();
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final Map<Object, Collection<CacheOperation>> operationCache = new ConcurrentHashMap<>(1024);

   @Override
   public boolean hasCacheOperations(Method method, @Nullable Class<?> targetClass) {
      return !CollectionUtils.isEmpty(this.getCacheOperations(method, targetClass, false));
   }

   @Override
   public @Nullable Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass) {
      return this.getCacheOperations(method, targetClass, true);
   }

   private @Nullable Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass, boolean cacheNull) {
      if (ReflectionUtils.isObjectMethod(method)) {
         return null;
      }

      Object cacheKey = this.getCacheKey(method, targetClass);
      Collection<CacheOperation> cached = this.operationCache.get(cacheKey);
      if (cached != null) {
         return cached != NULL_CACHING_MARKER ? cached : null;
      }

      Collection<CacheOperation> cacheOps = this.computeCacheOperations(method, targetClass);
      if (cacheOps != null) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Adding cacheable method '" + method.getName() + "' with operations: " + cacheOps);
         }

         this.operationCache.put(cacheKey, cacheOps);
      } else if (cacheNull) {
         this.operationCache.put(cacheKey, NULL_CACHING_MARKER);
      }

      return cacheOps;
   }

   protected Object getCacheKey(Method method, @Nullable Class<?> targetClass) {
      return new MethodClassKey(method, targetClass);
   }

   private @Nullable Collection<CacheOperation> computeCacheOperations(Method method, @Nullable Class<?> targetClass) {
      if (this.allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
         return null;
      }

      if (method.getDeclaringClass() == BeanFactoryAware.class) {
         return null;
      }

      Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
      Collection<CacheOperation> opDef = this.findCacheOperations(specificMethod);
      if (opDef != null) {
         return opDef;
      }

      opDef = this.findCacheOperations(specificMethod.getDeclaringClass());
      if (opDef != null && ClassUtils.isUserLevelMethod(method)) {
         return opDef;
      }

      if (specificMethod != method) {
         opDef = this.findCacheOperations(method);
         if (opDef != null) {
            return opDef;
         }

         opDef = this.findCacheOperations(method.getDeclaringClass());
         if (opDef != null && ClassUtils.isUserLevelMethod(method)) {
            return opDef;
         }
      }

      return null;
   }

   protected abstract @Nullable Collection<CacheOperation> findCacheOperations(Class<?> clazz);

   protected abstract @Nullable Collection<CacheOperation> findCacheOperations(Method method);

   protected boolean allowPublicMethodsOnly() {
      return false;
   }
}
