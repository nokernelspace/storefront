package org.springframework.util;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

final class MultiToSingleValueMapAdapter<K, V> implements Map<K, V>, Serializable {
   private final MultiValueMap<K, V> targetMap;
   private transient @Nullable Collection<V> values;
   private transient @Nullable Set<Entry<K, V>> entries;

   public MultiToSingleValueMapAdapter(MultiValueMap<K, V> targetMap) {
      Assert.notNull(targetMap, "'targetMap' must not be null");
      this.targetMap = targetMap;
   }

   @Override
   public int size() {
      return this.targetMap.size();
   }

   @Override
   public boolean isEmpty() {
      return this.targetMap.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return this.targetMap.containsKey(key);
   }

   @Override
   public boolean containsValue(@Nullable Object value) {
      Iterator<Entry<K, V>> i = this.entrySet().iterator();
      if (value == null) {
         while (i.hasNext()) {
            Entry<K, V> e = i.next();
            if (e.getValue() == null) {
               return true;
            }
         }
      } else {
         while (i.hasNext()) {
            Entry<K, V> e = i.next();
            if (value.equals(e.getValue())) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public @Nullable V get(Object key) {
      return this.adaptValue(this.targetMap.get(key));
   }

   @Override
   public @Nullable V put(K key, @Nullable V value) {
      return this.adaptValue(this.targetMap.put(key, this.adaptValue(value)));
   }

   @Override
   public @Nullable V remove(Object key) {
      return this.adaptValue(this.targetMap.remove(key));
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> map) {
      for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
         this.put((K)entry.getKey(), (V)entry.getValue());
      }
   }

   @Override
   public void clear() {
      this.targetMap.clear();
   }

   @Override
   public Set<K> keySet() {
      return this.targetMap.keySet();
   }

   @Override
   public Collection<V> values() {
      Collection<V> values = this.values;
      if (values == null) {
         final Collection<List<V>> targetValues = this.targetMap.values();
         values = new AbstractCollection<V>() {
            @Override
            public Iterator<V> iterator() {
               final Iterator<List<V>> targetIterator = targetValues.iterator();
               return new Iterator<V>() {
                  @Override
                  public boolean hasNext() {
                     return targetIterator.hasNext();
                  }

                  @Override
                  public V next() {
                     return targetIterator.next().get(0);
                  }
               };
            }

            @Override
            public int size() {
               return targetValues.size();
            }
         };
         this.values = values;
      }

      return values;
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      Set<Entry<K, V>> entries = this.entries;
      if (entries == null) {
         final Set<Entry<K, List<V>>> targetEntries = this.targetMap.entrySet();
         entries = new AbstractSet<Entry<K, V>>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
               final Iterator<Entry<K, List<V>>> targetIterator = targetEntries.iterator();
               return new Iterator<Entry<K, V>>() {
                  @Override
                  public boolean hasNext() {
                     return targetIterator.hasNext();
                  }

                  public Entry<K, V> next() {
                     Entry<K, List<V>> entry = targetIterator.next();
                     return new SimpleImmutableEntry<>(entry.getKey(), entry.getValue().get(0));
                  }
               };
            }

            @Override
            public int size() {
               return targetEntries.size();
            }
         };
         this.entries = entries;
      }

      return entries;
   }

   @Override
   public void forEach(BiConsumer<? super K, ? super V> action) {
      this.targetMap.forEach((k, vs) -> action.accept(k, vs.get(0)));
   }

   private @Nullable V adaptValue(@Nullable List<V> values) {
      return !CollectionUtils.isEmpty(values) ? values.get(0) : null;
   }

   private @Nullable List<V> adaptValue(@Nullable V value) {
      return value != null ? Collections.singletonList(value) : null;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      }

      if (other instanceof Map<?, ?> otherMap && this.size() == otherMap.size()) {
         try {
            for (Entry<K, V> e : this.entrySet()) {
               K key = e.getKey();
               V value = e.getValue();
               if (value == null) {
                  if (otherMap.get(key) != null || !otherMap.containsKey(key)) {
                     return false;
                  }
               } else if (!value.equals(otherMap.get(key))) {
                  return false;
               }
            }

            return true;
         } catch (ClassCastException | NullPointerException var7) {
         }
      }

      return false;
   }

   @Override
   public int hashCode() {
      return this.targetMap.hashCode();
   }

   @Override
   public String toString() {
      return this.targetMap.toString();
   }
}
