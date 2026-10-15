package org.springframework.util;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class FilteredMap<K, V> extends AbstractMap<K, V> {
   private final Map<K, V> delegate;
   private final Predicate<K> filter;

   public FilteredMap(Map<K, V> delegate, Predicate<K> filter) {
      Assert.notNull(delegate, "Delegate must not be null");
      Assert.notNull(filter, "Filter must not be null");
      this.delegate = delegate;
      this.filter = filter;
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return new FilteredSet<>(this.delegate.entrySet(), entry -> this.filter.test(entry.getKey()));
   }

   @Override
   public int size() {
      int size = 0;

      for (K k : this.delegate.keySet()) {
         if (this.filter.test(k)) {
            size++;
         }
      }

      return size;
   }

   @Override
   public boolean containsKey(Object key) {
      return this.delegate.containsKey(key) ? this.filter.test((K)key) : false;
   }

   @Override
   public @Nullable V get(Object key) {
      V value = this.delegate.get(key);
      return value != null && this.filter.test((K)key) ? value : null;
   }

   @Override
   public @Nullable V put(K key, V value) {
      V oldValue = this.delegate.put(key, value);
      return oldValue != null && this.filter.test(key) ? oldValue : null;
   }

   @Override
   public @Nullable V remove(Object key) {
      V oldValue = this.delegate.remove(key);
      return oldValue != null && this.filter.test((K)key) ? oldValue : null;
   }

   @Override
   public void clear() {
      this.delegate.clear();
   }

   @Override
   public Set<K> keySet() {
      return new FilteredSet<>(this.delegate.keySet(), this.filter);
   }
}
