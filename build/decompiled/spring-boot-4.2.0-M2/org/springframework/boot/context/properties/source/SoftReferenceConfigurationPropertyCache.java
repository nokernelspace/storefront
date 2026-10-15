package org.springframework.boot.context.properties.source;

import java.lang.ref.SoftReference;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

class SoftReferenceConfigurationPropertyCache<T> implements ConfigurationPropertyCaching {
   private static final Duration UNLIMITED = Duration.ZERO;
   static final ConfigurationPropertyCaching.CacheOverride NO_OP_OVERRIDE = () -> {};
   private final boolean neverExpire;
   private volatile @Nullable Duration timeToLive;
   private volatile SoftReference<@Nullable T> value = new SoftReference<>(null);
   private volatile @Nullable Instant lastAccessed = this.now();

   SoftReferenceConfigurationPropertyCache(boolean neverExpire) {
      this.neverExpire = neverExpire;
   }

   @Override
   public void enable() {
      this.timeToLive = UNLIMITED;
   }

   @Override
   public void disable() {
      this.timeToLive = null;
   }

   @Override
   public void setTimeToLive(@Nullable Duration timeToLive) {
      this.timeToLive = timeToLive != null && !timeToLive.isZero() ? timeToLive : null;
   }

   @Override
   public void clear() {
      this.lastAccessed = null;
   }

   @Override
   public ConfigurationPropertyCaching.CacheOverride override() {
      if (this.neverExpire) {
         return NO_OP_OVERRIDE;
      }

      SoftReferenceConfigurationPropertyCache.ActiveCacheOverride override = new SoftReferenceConfigurationPropertyCache.ActiveCacheOverride(this);
      if (override.timeToLive() == null) {
         this.clear();
      }

      this.timeToLive = UNLIMITED;
      return override;
   }

   void restore(SoftReferenceConfigurationPropertyCache.ActiveCacheOverride override) {
      this.timeToLive = override.timeToLive();
      this.lastAccessed = override.lastAccessed();
   }

   T get(Supplier<T> factory, UnaryOperator<T> refreshAction) {
      T value = this.getValue();
      if (value == null) {
         value = refreshAction.apply(factory.get());
         this.setValue(value);
      } else if (this.hasExpired()) {
         value = refreshAction.apply(value);
         this.setValue(value);
      }

      if (!this.neverExpire) {
         this.lastAccessed = this.now();
      }

      return value;
   }

   private boolean hasExpired() {
      if (this.neverExpire) {
         return false;
      }

      Duration timeToLive = this.timeToLive;
      Instant lastAccessed = this.lastAccessed;
      return timeToLive != null && lastAccessed != null ? !UNLIMITED.equals(timeToLive) && this.now().isAfter(lastAccessed.plus(timeToLive)) : true;
   }

   protected Instant now() {
      return Instant.now();
   }

   protected @Nullable T getValue() {
      return this.value.get();
   }

   protected void setValue(T value) {
      this.value = new SoftReference<>(value);
   }

   private record ActiveCacheOverride(
      SoftReferenceConfigurationPropertyCache<?> cache, @Nullable Duration timeToLive, @Nullable Instant lastAccessed, AtomicBoolean active
   ) implements ConfigurationPropertyCaching.CacheOverride {
      ActiveCacheOverride(SoftReferenceConfigurationPropertyCache<?> cache) {
         this(cache, cache.timeToLive, cache.lastAccessed, new AtomicBoolean());
      }

      @Override
      public void close() {
         if (this.active().compareAndSet(false, true)) {
            this.cache.restore(this);
         }
      }
   }
}
