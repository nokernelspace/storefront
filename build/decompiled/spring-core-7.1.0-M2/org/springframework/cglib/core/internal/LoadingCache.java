package org.springframework.cglib.core.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class LoadingCache<K, KK, V> {
   protected final ConcurrentMap<KK, Object> map;
   protected final Function<K, V> loader;
   protected final Function<K, KK> keyMapper;
   public static final Function IDENTITY = key -> key;

   public LoadingCache(Function<K, KK> keyMapper, Function<K, V> loader) {
      this.keyMapper = keyMapper;
      this.loader = loader;
      this.map = (ConcurrentMap<KK, Object>)(new ConcurrentHashMap<>());
   }

   public static <K> Function<K, K> identity() {
      return IDENTITY;
   }

   public V get(K key) {
      KK cacheKey = this.keyMapper.apply(key);
      Object v = this.map.get(cacheKey);
      return (V)(v != null && !(v instanceof FutureTask) ? v : this.createEntry(key, cacheKey, v));
   }

   protected V createEntry(final K key, KK cacheKey, Object v) {
      boolean creator = false;
      FutureTask<V> task;
      if (v != null) {
         task = (FutureTask<V>)v;
      } else {
         task = new FutureTask<>(() -> this.loader.apply(key));
         Object prevTask = this.map.putIfAbsent(cacheKey, task);
         if (prevTask == null) {
            creator = true;
            task.run();
         } else {
            if (!(prevTask instanceof FutureTask)) {
               return (V)prevTask;
            }

            task = (FutureTask<V>)prevTask;
         }
      }

      V result;
      try {
         result = task.get();
      } catch (InterruptedException e) {
         throw new IllegalStateException("Interrupted while loading cache item", e);
      } catch (ExecutionException e) {
         Throwable cause = e.getCause();
         if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
         }

         throw new IllegalStateException("Unable to load cache item", cause);
      }

      if (creator) {
         this.map.put(cacheKey, result);
      }

      return result;
   }
}
