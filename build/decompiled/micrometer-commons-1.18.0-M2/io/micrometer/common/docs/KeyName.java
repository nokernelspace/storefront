package io.micrometer.common.docs;

import io.micrometer.common.KeyValue;
import java.util.Arrays;
import java.util.function.Predicate;

public interface KeyName {
   static KeyName[] merge(KeyName[]... keyNames) {
      return Arrays.stream(keyNames).flatMap(Arrays::stream).toArray(KeyName[]::new);
   }

   default KeyValue withValue(String value) {
      return KeyValue.of(this, value);
   }

   default KeyValue withNoneValue() {
      return KeyValue.of(this, "none");
   }

   default KeyValue withValue(String value, Predicate<Object> validator) {
      return KeyValue.of(this, value, validator);
   }

   String asString();

   default boolean isRequired() {
      return true;
   }
}
