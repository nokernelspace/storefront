package org.springframework.cache.support;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

public abstract class AbstractValueAdaptingCache implements Cache {
   private final boolean allowNullValues;

   protected AbstractValueAdaptingCache(boolean allowNullValues) {
      this.allowNullValues = allowNullValues;
   }

   public final boolean isAllowNullValues() {
      return this.allowNullValues;
   }

   @Override
   public Cache.@Nullable ValueWrapper get(Object key) {
      return this.toValueWrapper(this.lookup(key));
   }

   @Override
   public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
      Object value = this.fromStoreValue(this.lookup(key));
      if (value != null && type != null && !type.isInstance(value)) {
         throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
      } else {
         return (T)value;
      }
   }

   protected abstract @Nullable Object lookup(Object key);

   protected @Nullable Object fromStoreValue(@Nullable Object storeValue) {
      return this.allowNullValues && storeValue instanceof NullValue ? null : storeValue;
   }

   protected Object toStoreValue(@Nullable Object userValue) {
      if (userValue == null) {
         if (this.allowNullValues) {
            return NullValue.INSTANCE;
         } else {
            throw new IllegalArgumentException("Cache '" + this.getName() + "' is configured to not allow null values but null was provided");
         }
      } else {
         return userValue;
      }
   }

   protected Cache.@Nullable ValueWrapper toValueWrapper(@Nullable Object storeValue) {
      return storeValue != null ? new SimpleValueWrapper(this.fromStoreValue(storeValue)) : null;
   }
}
