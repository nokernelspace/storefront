package org.springframework.cache.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.Assert;

public abstract class AbstractCacheResolver implements CacheResolver, InitializingBean {
   private @Nullable CacheManager cacheManager;

   protected AbstractCacheResolver() {
   }

   protected AbstractCacheResolver(CacheManager cacheManager) {
      this.cacheManager = cacheManager;
   }

   public void setCacheManager(CacheManager cacheManager) {
      this.cacheManager = cacheManager;
   }

   public CacheManager getCacheManager() {
      Assert.state(this.cacheManager != null, "No CacheManager set");
      return this.cacheManager;
   }

   public void afterPropertiesSet() {
      Assert.notNull(this.cacheManager, "CacheManager is required");
   }

   @Override
   public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
      Collection<String> cacheNames = this.getCacheNames(context);
      if (cacheNames == null) {
         return Collections.emptyList();
      }

      Collection<Cache> result = new ArrayList<>(cacheNames.size());

      for (String cacheName : cacheNames) {
         Cache cache = this.getCacheManager().getCache(cacheName);
         if (cache == null) {
            throw new IllegalArgumentException("Cannot find cache named '" + cacheName + "' for " + context.getOperation());
         }

         result.add(cache);
      }

      return result;
   }

   protected abstract @Nullable Collection<String> getCacheNames(CacheOperationInvocationContext<?> context);
}
