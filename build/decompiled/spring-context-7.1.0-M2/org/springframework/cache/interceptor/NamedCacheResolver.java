package org.springframework.cache.interceptor;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.CacheManager;

public class NamedCacheResolver extends AbstractCacheResolver {
   private @Nullable Collection<String> cacheNames;

   public NamedCacheResolver() {
   }

   public NamedCacheResolver(CacheManager cacheManager, String... cacheNames) {
      super(cacheManager);
      this.cacheNames = List.of(cacheNames);
   }

   public void setCacheNames(Collection<String> cacheNames) {
      this.cacheNames = cacheNames;
   }

   @Override
   protected @Nullable Collection<String> getCacheNames(CacheOperationInvocationContext<?> context) {
      return this.cacheNames;
   }
}
