package org.springframework.cache.support;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;

public class SimpleValueWrapper implements Cache.ValueWrapper {
   private final @Nullable Object value;

   public SimpleValueWrapper(@Nullable Object value) {
      this.value = value;
   }

   @Override
   public @Nullable Object get() {
      return this.value;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof Cache.ValueWrapper wrapper && Objects.equals(this.get(), wrapper.get());
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(this.value);
   }

   @Override
   public String toString() {
      return "ValueWrapper for [" + this.value + "]";
   }
}
