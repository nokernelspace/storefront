package io.micrometer.common;

import java.util.function.Predicate;

class ValidatedKeyValue<T> implements KeyValue {
   private final String key;
   private final String value;

   ValidatedKeyValue(String key, T value, Predicate<? super T> validator) {
      this.key = key;
      this.value = String.valueOf(this.assertValue(value, validator));
   }

   @Override
   public String getKey() {
      return this.key;
   }

   @Override
   public String getValue() {
      return this.value;
   }

   private T assertValue(T value, Predicate<? super T> validator) {
      if (!validator.test(value)) {
         throw new IllegalArgumentException("Argument [" + value + "] does not follow required format for key [" + this.key + "]");
      } else {
         return value;
      }
   }

   @Override
   public String toString() {
      return "keyValue(" + this.key + "=" + this.value + ")";
   }
}
