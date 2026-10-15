package org.springframework.cache.interceptor;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

public class SimpleCacheErrorHandler implements CacheErrorHandler {
   @Override
   public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
      throw exception;
   }

   @Override
   public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
      throw exception;
   }

   @Override
   public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
      throw exception;
   }

   @Override
   public void handleCacheClearError(RuntimeException exception, Cache cache) {
      throw exception;
   }
}
