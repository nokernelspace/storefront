package org.springframework.cache.support;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class NoOpCacheManager implements CacheManager {
   private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);

   @Override
   public @Nullable Cache getCache(String name) {
      return this.cacheMap.computeIfAbsent(name, NoOpCache::new);
   }

   @Override
   public Collection<String> getCacheNames() {
      return Collections.unmodifiableSet(this.cacheMap.keySet());
   }

   @Override
   public void resetCaches() {
      this.cacheMap.clear();
   }
}
