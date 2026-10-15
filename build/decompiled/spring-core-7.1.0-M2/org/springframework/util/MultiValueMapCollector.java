package org.springframework.util;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;

public final class MultiValueMapCollector<T, K, V> implements Collector<T, MultiValueMap<K, V>, MultiValueMap<K, V>> {
   private final Function<T, K> keyFunction;
   private final Function<T, V> valueFunction;

   private MultiValueMapCollector(Function<T, K> keyFunction, Function<T, V> valueFunction) {
      this.keyFunction = keyFunction;
      this.valueFunction = valueFunction;
   }

   public static <T, K, V> MultiValueMapCollector<T, K, V> of(Function<T, K> keyFunction, Function<T, V> valueFunction) {
      return new MultiValueMapCollector<>(keyFunction, valueFunction);
   }

   public static <K, V> MultiValueMapCollector<V, K, V> indexingBy(Function<V, K> indexer) {
      return new MultiValueMapCollector<>(indexer, Function.identity());
   }

   @Override
   public Supplier<MultiValueMap<K, V>> supplier() {
      return () -> CollectionUtils.toMultiValueMap(new LinkedHashMap<>());
   }

   @Override
   public BiConsumer<MultiValueMap<K, V>, T> accumulator() {
      return (map, t) -> map.add(this.keyFunction.apply(t), this.valueFunction.apply(t));
   }

   @Override
   public BinaryOperator<MultiValueMap<K, V>> combiner() {
      return (map1, map2) -> {
         for (Entry<K, List<V>> entry : map2.entrySet()) {
            map1.addAll(entry.getKey(), entry.getValue());
         }

         return map1;
      };
   }

   @Override
   public Function<MultiValueMap<K, V>, MultiValueMap<K, V>> finisher() {
      return Function.identity();
   }

   @Override
   public Set<Characteristics> characteristics() {
      return EnumSet.of(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
   }
}
