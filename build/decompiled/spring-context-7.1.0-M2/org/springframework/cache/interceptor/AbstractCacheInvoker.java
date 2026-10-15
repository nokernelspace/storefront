package org.springframework.cache.interceptor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.util.function.SingletonSupplier;

public abstract class AbstractCacheInvoker {
   protected SingletonSupplier<CacheErrorHandler> errorHandler;

   protected AbstractCacheInvoker() {
      this.errorHandler = SingletonSupplier.of(SimpleCacheErrorHandler::new);
   }

   protected AbstractCacheInvoker(CacheErrorHandler errorHandler) {
      this.errorHandler = SingletonSupplier.of(errorHandler);
   }

   public void setErrorHandler(CacheErrorHandler errorHandler) {
      this.errorHandler = SingletonSupplier.of(errorHandler);
   }

   public CacheErrorHandler getErrorHandler() {
      return (CacheErrorHandler)this.errorHandler.obtain();
   }

   protected Cache.@Nullable ValueWrapper doGet(Cache cache, Object key) {
      try {
         return cache.get(key);
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCacheGetError(ex, cache, key);
         return null;
      }
   }

   protected <T> @Nullable T doGet(Cache cache, Object key, Callable<T> valueLoader) {
      try {
         return cache.get(key, valueLoader);
      } catch (Cache.ValueRetrievalException ex) {
         throw ex;
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCacheGetError(ex, cache, key);

         try {
            return valueLoader.call();
         } catch (Exception ex2) {
            throw new Cache.ValueRetrievalException(key, valueLoader, ex);
         }
      }
   }

   protected @Nullable CompletableFuture<?> doRetrieve(Cache cache, Object key) {
      try {
         return cache.retrieve(key);
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCacheGetError(ex, cache, key);
         return null;
      }
   }

   protected <T> CompletableFuture<T> doRetrieve(Cache cache, Object key, Supplier<CompletableFuture<T>> valueLoader) {
      try {
         return cache.retrieve(key, valueLoader);
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCacheGetError(ex, cache, key);
         return valueLoader.get();
      }
   }

   protected void doPut(Cache cache, Object key, @Nullable Object value) {
      try {
         cache.put(key, value);
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCachePutError(ex, cache, key, value);
      }
   }

   protected void doEvict(Cache cache, Object key, boolean immediate) {
      try {
         if (immediate) {
            cache.evictIfPresent(key);
         } else {
            cache.evict(key);
         }
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCacheEvictError(ex, cache, key);
      }
   }

   protected void doClear(Cache cache, boolean immediate) {
      try {
         if (immediate) {
            cache.invalidate();
         } else {
            cache.clear();
         }
      } catch (RuntimeException ex) {
         this.getErrorHandler().handleCacheClearError(ex, cache);
      }
   }
}
