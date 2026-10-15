package org.springframework.util;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface MultiValueMap<K, V> extends Map<K, List<V>> {
   @Nullable V getFirst(K key);

   void add(K key, @Nullable V value);

   void addAll(K key, List<? extends V> values);

   void addAll(MultiValueMap<K, V> values);

   default void addIfAbsent(K key, @Nullable V value) {
      if (!this.containsKey(key)) {
         this.add(key, value);
      }
   }

   void set(K key, @Nullable V value);

   void setAll(Map<K, V> values);

   Map<K, V> toSingleValueMap();

   default Map<K, V> asSingleValueMap() {
      return new MultiToSingleValueMapAdapter<>(this);
   }

   static <K, V> MultiValueMap<K, V> fromSingleValue(Map<K, V> map) {
      Assert.notNull(map, "Map must not be null");
      return new SingleToMultiValueMapAdapter<>(map);
   }

   static <K, V> MultiValueMap<K, V> fromMultiValue(Map<K, List<V>> map) {
      Assert.notNull(map, "Map must not be null");
      return new MultiValueMapAdapter<>(map);
   }
}
