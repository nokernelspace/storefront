package io.micrometer.common;

import io.micrometer.common.docs.KeyName;
import java.util.function.Function;
import java.util.function.Predicate;

public interface KeyValue extends Comparable<KeyValue> {
   String NONE_VALUE = "none";

   String getKey();

   String getValue();

   static KeyValue of(String key, String value) {
      return new ImmutableKeyValue(key, value);
   }

   static KeyValue of(KeyName keyName, String value) {
      return of(keyName.asString(), value);
   }

   static <E> KeyValue of(E element, Function<E, String> keyExtractor, Function<E, String> valueExtractor) {
      return of(keyExtractor.apply(element), valueExtractor.apply(element));
   }

   static <T> KeyValue of(String key, T value, Predicate<? super T> validator) {
      return new ValidatedKeyValue<>(key, value, validator);
   }

   static <T> KeyValue of(KeyName keyName, T value, Predicate<? super T> validator) {
      return of(keyName.asString(), value, validator);
   }

   default int compareTo(KeyValue o) {
      return this.getKey().compareTo(o.getKey());
   }
}
