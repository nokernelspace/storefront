package org.springframework.cache.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;

public interface CachingConfigurer {
   default @Nullable CacheManager cacheManager() {
      return null;
   }

   default @Nullable CacheResolver cacheResolver() {
      return null;
   }

   default @Nullable KeyGenerator keyGenerator() {
      return null;
   }

   default @Nullable CacheErrorHandler errorHandler() {
      return null;
   }
}
