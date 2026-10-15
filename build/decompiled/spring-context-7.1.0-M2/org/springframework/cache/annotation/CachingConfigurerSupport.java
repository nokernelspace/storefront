package org.springframework.cache.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;

@Deprecated(since = "6.0")
public class CachingConfigurerSupport implements CachingConfigurer {
   @Override
   public @Nullable CacheManager cacheManager() {
      return null;
   }

   @Override
   public @Nullable CacheResolver cacheResolver() {
      return null;
   }

   @Override
   public @Nullable KeyGenerator keyGenerator() {
      return null;
   }

   @Override
   public @Nullable CacheErrorHandler errorHandler() {
      return null;
   }
}
