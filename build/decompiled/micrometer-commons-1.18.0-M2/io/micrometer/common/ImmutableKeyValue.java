package io.micrometer.common;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

class ImmutableKeyValue implements KeyValue {
   private final String key;
   private final String value;

   ImmutableKeyValue(String key, String value) {
      Objects.requireNonNull(key);
      Objects.requireNonNull(value);
      this.key = key;
      this.value = value;
   }

   @Override
   public String getKey() {
      return this.key;
   }

   @Override
   public String getValue() {
      return this.value;
   }

   @Override
   public boolean equals(@Nullable Object o) {
      if (this == o) {
         return true;
      }

      if (!(o instanceof ImmutableKeyValue)) {
         return false;
      }

      KeyValue that = (KeyValue)o;
      return Objects.equals(this.key, that.getKey()) && Objects.equals(this.value, that.getValue());
   }

   @Override
   public int hashCode() {
      int result = this.key.hashCode();
      return 31 * result + this.value.hashCode();
   }

   @Override
   public String toString() {
      return "keyValue(" + this.key + "=" + this.value + ")";
   }
}
