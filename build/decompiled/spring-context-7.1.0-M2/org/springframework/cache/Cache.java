package org.springframework.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface Cache {
   String getName();

   Object getNativeCache();

   Cache.@Nullable ValueWrapper get(Object key);

   <T> @Nullable T get(Object key, @Nullable Class<T> type);

   <T> @Nullable T get(Object key, Callable<T> valueLoader);

   default @Nullable CompletableFuture<?> retrieve(Object key) {
      throw new UnsupportedOperationException(this.getClass().getName() + " does not support CompletableFuture-based retrieval");
   }

   default <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
      throw new UnsupportedOperationException(this.getClass().getName() + " does not support CompletableFuture-based retrieval");
   }

   void put(Object key, @Nullable Object value);

   default Cache.@Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
      Cache.ValueWrapper existingValue = this.get(key);
      if (existingValue == null) {
         this.put(key, value);
      }

      return existingValue;
   }

   void evict(Object key);

   default boolean evictIfPresent(Object key) {
      this.evict(key);
      return false;
   }

   void clear();

   default boolean invalidate() {
      this.clear();
      return false;
   }

   class ValueRetrievalException extends RuntimeException {
      private final @Nullable Object key;

      public ValueRetrievalException(@Nullable Object key, Callable<?> loader, @Nullable Throwable ex) {
         super(String.format("Value for key '%s' could not be loaded using '%s'", key, loader), ex);
         this.key = key;
      }

      public @Nullable Object getKey() {
         return this.key;
      }
   }

   @FunctionalInterface
   interface ValueWrapper {
      @Nullable Object get();
   }
}
