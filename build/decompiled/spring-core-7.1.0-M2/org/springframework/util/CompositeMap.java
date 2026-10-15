package org.springframework.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

final class CompositeMap<K, V> implements Map<K, V> {
   private final Map<K, V> first;
   private final Map<K, V> second;
   private final @Nullable BiFunction<K, V, V> putFunction;
   private final @Nullable Consumer<Map<K, V>> putAllFunction;

   CompositeMap(Map<K, V> first, Map<K, V> second) {
      this(first, second, null, null);
   }

   CompositeMap(Map<K, V> first, Map<K, V> second, @Nullable BiFunction<K, V, V> putFunction, @Nullable Consumer<Map<K, V>> putAllFunction) {
      Assert.notNull(first, "First must not be null");
      Assert.notNull(second, "Second must not be null");
      this.first = first;
      this.second = new FilteredMap<>(second, key -> !this.first.containsKey(key));
      this.putFunction = putFunction;
      this.putAllFunction = putAllFunction;
   }

   @Override
   public int size() {
      return this.first.size() + this.second.size();
   }

   @Override
   public boolean isEmpty() {
      return this.first.isEmpty() && this.second.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return this.first.containsKey(key) ? true : this.second.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      return this.first.containsValue(value) ? true : this.second.containsValue(value);
   }

   @Override
   public @Nullable V get(Object key) {
      V firstResult = this.first.get(key);
      return firstResult != null ? firstResult : this.second.get(key);
   }

   @Override
   public V put(K key, V value) {
      if (this.putFunction == null) {
         throw new UnsupportedOperationException();
      } else {
         return this.putFunction.apply(key, value);
      }
   }

   @Override
   public @Nullable V remove(Object key) {
      V firstResult = this.first.remove(key);
      V secondResult = this.second.remove(key);
      return firstResult != null ? firstResult : secondResult;
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      if (this.putAllFunction != null) {
         this.putAllFunction.accept((Map<K, V>)m);
      } else {
         for (Entry<? extends K, ? extends V> e : m.entrySet()) {
            this.put((K)e.getKey(), (V)e.getValue());
         }
      }
   }

   @Override
   public void clear() {
      this.first.clear();
      this.second.clear();
   }

   @Override
   public Set<K> keySet() {
      return new CompositeSet<>(this.first.keySet(), this.second.keySet());
   }

   @Override
   public Collection<V> values() {
      return new CompositeCollection<>(this.first.values(), this.second.values());
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return new CompositeSet<>(this.first.entrySet(), this.second.entrySet());
   }

   @Override
   public String toString() {
      Iterator<Entry<K, V>> i = this.entrySet().iterator();
      if (!i.hasNext()) {
         return "{}";
      }

      StringBuilder sb = new StringBuilder();
      sb.append('{');

      while (true) {
         Entry<K, V> e = i.next();
         K key = e.getKey();
         V value = e.getValue();
         sb.append(key == this ? "(this Map)" : key);
         sb.append('=');
         sb.append(value == this ? "(this Map)" : value);
         if (!i.hasNext()) {
            return sb.append('}').toString();
         }

         sb.append(',').append(' ');
      }
   }
}
