package org.springframework.cache.support;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.CollectionUtils;

public abstract class AbstractCacheManager implements CacheManager, InitializingBean {
   private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);
   private volatile Set<String> cacheNames = Collections.emptySet();

   public void afterPropertiesSet() {
      this.initializeCaches();
   }

   public void initializeCaches() {
      Collection<? extends Cache> caches = this.loadCaches();
      synchronized (this.cacheMap) {
         this.cacheNames = Collections.emptySet();
         this.cacheMap.clear();
         Set<String> cacheNames = CollectionUtils.newLinkedHashSet(caches.size());

         for (Cache cache : caches) {
            String name = cache.getName();
            this.cacheMap.put(name, this.decorateCache(cache));
            cacheNames.add(name);
         }

         this.cacheNames = Collections.unmodifiableSet(cacheNames);
      }
   }

   protected abstract Collection<? extends Cache> loadCaches();

   @Override
   public @Nullable Cache getCache(String name) {
      Cache cache = this.cacheMap.get(name);
      if (cache != null) {
         return cache;
      }

      Cache missingCache = this.getMissingCache(name);
      if (missingCache != null) {
         synchronized (this.cacheMap) {
            cache = this.cacheMap.get(name);
            if (cache == null) {
               cache = this.decorateCache(missingCache);
               this.cacheMap.put(name, cache);
               this.updateCacheNames(name);
            }
         }
      }

      return cache;
   }

   @Override
   public Collection<String> getCacheNames() {
      return this.cacheNames;
   }

   @Override
   public void resetCaches() {
      synchronized (this.cacheMap) {
         this.cacheMap.values().forEach(Cache::clear);
      }
   }

   protected final @Nullable Cache lookupCache(String name) {
      return this.cacheMap.get(name);
   }

   private void updateCacheNames(String name) {
      Set<String> cacheNames = new LinkedHashSet<>(this.cacheNames);
      cacheNames.add(name);
      this.cacheNames = Collections.unmodifiableSet(cacheNames);
   }

   protected Cache decorateCache(Cache cache) {
      return cache;
   }

   protected @Nullable Cache getMissingCache(String name) {
      return null;
   }
}
