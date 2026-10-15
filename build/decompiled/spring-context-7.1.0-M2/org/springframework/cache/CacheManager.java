package org.springframework.cache;

import java.util.Collection;
import org.jspecify.annotations.Nullable;

public interface CacheManager {
   @Nullable Cache getCache(String name);

   Collection<String> getCacheNames();

   default void resetCaches() {
      for (String cacheName : this.getCacheNames()) {
         Cache cache = this.getCache(cacheName);
         if (cache != null) {
            cache.clear();
         }
      }
   }
}
