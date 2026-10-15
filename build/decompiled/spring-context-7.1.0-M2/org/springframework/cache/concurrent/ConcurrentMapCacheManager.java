package org.springframework.cache.concurrent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.serializer.support.SerializationDelegate;

public class ConcurrentMapCacheManager implements CacheManager, BeanClassLoaderAware {
   private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);
   private volatile boolean dynamic = true;
   private boolean allowNullValues = true;
   private boolean storeByValue = false;
   private @Nullable SerializationDelegate serialization;

   public ConcurrentMapCacheManager() {
   }

   public ConcurrentMapCacheManager(String... cacheNames) {
      this.setCacheNames(Arrays.asList(cacheNames));
   }

   public void setCacheNames(@Nullable Collection<String> cacheNames) {
      if (cacheNames != null) {
         for (String name : cacheNames) {
            this.cacheMap.put(name, this.createConcurrentMapCache(name));
         }

         this.dynamic = false;
      } else {
         this.dynamic = true;
      }
   }

   public void setAllowNullValues(boolean allowNullValues) {
      if (allowNullValues != this.allowNullValues) {
         this.allowNullValues = allowNullValues;
         this.recreateCaches();
      }
   }

   public boolean isAllowNullValues() {
      return this.allowNullValues;
   }

   public void setStoreByValue(boolean storeByValue) {
      if (storeByValue != this.storeByValue) {
         this.storeByValue = storeByValue;
         this.recreateCaches();
      }
   }

   public boolean isStoreByValue() {
      return this.storeByValue;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.serialization = new SerializationDelegate(classLoader);
      if (this.isStoreByValue()) {
         this.recreateCaches();
      }
   }

   @Override
   public @Nullable Cache getCache(String name) {
      Cache cache = this.cacheMap.get(name);
      if (cache == null && this.dynamic) {
         cache = this.cacheMap.computeIfAbsent(name, this::createConcurrentMapCache);
      }

      return cache;
   }

   @Override
   public Collection<String> getCacheNames() {
      return Collections.unmodifiableSet(this.cacheMap.keySet());
   }

   @Override
   public void resetCaches() {
      this.cacheMap.values().forEach(Cache::clear);
      if (this.dynamic) {
         this.cacheMap.clear();
      }
   }

   public void removeCache(String name) {
      this.cacheMap.remove(name);
   }

   private void recreateCaches() {
      for (Entry<String, Cache> entry : this.cacheMap.entrySet()) {
         entry.setValue(this.createConcurrentMapCache(entry.getKey()));
      }
   }

   protected Cache createConcurrentMapCache(String name) {
      SerializationDelegate actualSerialization = this.isStoreByValue() ? this.serialization : null;
      return new ConcurrentMapCache(name, new ConcurrentHashMap<>(256), this.isAllowNullValues(), actualSerialization);
   }
}
