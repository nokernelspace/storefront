package org.springframework.cache.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public class CompositeCacheManager implements CacheManager, InitializingBean {
   private final List<CacheManager> cacheManagers = new ArrayList<>();
   private boolean fallbackToNoOpCache = false;

   public CompositeCacheManager() {
   }

   public CompositeCacheManager(CacheManager... cacheManagers) {
      this.setCacheManagers(Arrays.asList(cacheManagers));
   }

   public void setCacheManagers(Collection<CacheManager> cacheManagers) {
      this.cacheManagers.addAll(cacheManagers);
   }

   public void setFallbackToNoOpCache(boolean fallbackToNoOpCache) {
      this.fallbackToNoOpCache = fallbackToNoOpCache;
   }

   public void afterPropertiesSet() {
      if (this.fallbackToNoOpCache) {
         this.cacheManagers.add(new NoOpCacheManager());
      }
   }

   @Override
   public @Nullable Cache getCache(String name) {
      for (CacheManager cacheManager : this.cacheManagers) {
         Cache cache = cacheManager.getCache(name);
         if (cache != null) {
            return cache;
         }
      }

      return null;
   }

   @Override
   public Collection<String> getCacheNames() {
      Set<String> names = new LinkedHashSet<>();

      for (CacheManager manager : this.cacheManagers) {
         names.addAll(manager.getCacheNames());
      }

      return Collections.unmodifiableSet(names);
   }

   @Override
   public void resetCaches() {
      for (CacheManager manager : this.cacheManagers) {
         manager.resetCaches();
      }
   }
}
