package org.springframework.cache.interceptor;

import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Contract;

public class SimpleCacheResolver extends AbstractCacheResolver {
   public SimpleCacheResolver() {
   }

   public SimpleCacheResolver(CacheManager cacheManager) {
      super(cacheManager);
   }

   @Override
   protected Collection<String> getCacheNames(CacheOperationInvocationContext<?> context) {
      return context.getOperation().getCacheNames();
   }

   @Contract("null -> null; !null -> !null")
   static @Nullable SimpleCacheResolver of(@Nullable CacheManager cacheManager) {
      return cacheManager != null ? new SimpleCacheResolver(cacheManager) : null;
   }
}
