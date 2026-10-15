package org.springframework.cache.interceptor;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

public interface CacheErrorHandler {
   void handleCacheGetError(RuntimeException exception, Cache cache, Object key);

   void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value);

   void handleCacheEvictError(RuntimeException exception, Cache cache, Object key);

   void handleCacheClearError(RuntimeException exception, Cache cache);
}
