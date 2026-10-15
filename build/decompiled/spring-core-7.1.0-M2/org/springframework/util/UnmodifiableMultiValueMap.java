package org.springframework.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

final class UnmodifiableMultiValueMap<K, V> implements MultiValueMap<K, V>, Serializable {
   private static final long serialVersionUID = -8697084563854098920L;
   private final MultiValueMap<K, V> delegate;
   private transient @Nullable Set<K> keySet;
   private transient @Nullable Set<Entry<K, List<V>>> entrySet;
   private transient @Nullable Collection<List<V>> values;

   public UnmodifiableMultiValueMap(MultiValueMap<? extends K, ? extends V> delegate) {
      Assert.notNull(delegate, "Delegate must not be null");
      this.delegate = (MultiValueMap<K, V>)delegate;
   }

   @Override
   public int size() {
      return this.delegate.size();
   }

   @Override
   public boolean isEmpty() {
      return this.delegate.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return this.delegate.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      return this.delegate.containsValue(value);
   }

   public @Nullable List<V> get(Object key) {
      List<V> result = this.delegate.get(key);
      return result != null ? Collections.unmodifiableList(result) : null;
   }

   @Override
   public @Nullable V getFirst(K key) {
      return this.delegate.getFirst(key);
   }

   public List<V> getOrDefault(Object key, List<V> defaultValue) {
      List<V> result = this.delegate.getOrDefault(key, defaultValue);
      if (result != defaultValue) {
         result = Collections.unmodifiableList(result);
      }

      return result;
   }

   @Override
   public void forEach(BiConsumer<? super K, ? super List<V>> action) {
      this.delegate.forEach((k, vs) -> action.accept(k, Collections.unmodifiableList((List<? extends V>)vs)));
   }

   @Override
   public Map<K, V> toSingleValueMap() {
      return this.delegate.toSingleValueMap();
   }

   @Override
   public Map<K, V> asSingleValueMap() {
      return this.delegate.asSingleValueMap();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || this.delegate.equals(other);
   }

   @Override
   public int hashCode() {
      return this.delegate.hashCode();
   }

   @Override
   public String toString() {
      return this.delegate.toString();
   }

   @Override
   public Set<K> keySet() {
      Set<K> keySet = this.keySet;
      if (keySet == null) {
         keySet = Collections.unmodifiableSet(this.delegate.keySet());
         this.keySet = keySet;
      }

      return keySet;
   }

   @Override
   public Set<Entry<K, List<V>>> entrySet() {
      Set<Entry<K, List<V>>> entrySet = this.entrySet;
      if (entrySet == null) {
         entrySet = new UnmodifiableMultiValueMap.UnmodifiableEntrySet<>(this.delegate.entrySet());
         this.entrySet = entrySet;
      }

      return entrySet;
   }

   @Override
   public Collection<List<V>> values() {
      Collection<List<V>> values = this.values;
      if (values == null) {
         values = new UnmodifiableMultiValueMap.UnmodifiableValueCollection<>(this.delegate.values());
         this.values = values;
      }

      return values;
   }

   public @Nullable List<V> put(K key, List<V> value) {
      throw new UnsupportedOperationException();
   }

   public List<V> putIfAbsent(K key, List<V> value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void putAll(Map<? extends K, ? extends List<V>> m) {
      throw new UnsupportedOperationException();
   }

   public List<V> remove(Object key) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void add(K key, @Nullable V value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void addAll(K key, List<? extends V> values) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void addAll(MultiValueMap<K, V> values) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void addIfAbsent(K key, @Nullable V value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void set(K key, @Nullable V value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAll(Map<K, V> values) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void replaceAll(BiFunction<? super K, ? super List<V>, ? extends List<V>> function) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean remove(Object key, Object value) {
      throw new UnsupportedOperationException();
   }

   public boolean replace(K key, List<V> oldValue, List<V> newValue) {
      throw new UnsupportedOperationException();
   }

   public List<V> replace(K key, List<V> value) {
      throw new UnsupportedOperationException();
   }

   public List<V> computeIfAbsent(K key, Function<? super K, ? extends List<V>> mappingFunction) {
      throw new UnsupportedOperationException();
   }

   public List<V> computeIfPresent(K key, BiFunction<? super K, ? super List<V>, ? extends List<V>> remappingFunction) {
      throw new UnsupportedOperationException();
   }

   public List<V> compute(K key, BiFunction<? super K, ? super List<V>, ? extends List<V>> remappingFunction) {
      throw new UnsupportedOperationException();
   }

   public List<V> merge(K key, List<V> value, BiFunction<? super List<V>, ? super List<V>, ? extends List<V>> remappingFunction) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void clear() {
      throw new UnsupportedOperationException();
   }

   private static class UnmodifiableEntrySet<K, V> implements Set<Entry<K, List<V>>>, Serializable {
      private static final long serialVersionUID = 2407578793783925203L;
      private final Set<Entry<K, List<V>>> delegate;

      public UnmodifiableEntrySet(Set<? extends Entry<? extends K, ? extends List<? extends V>>> delegate) {
         this.delegate = (Set<Entry<K, List<V>>>)delegate;
      }

      @Override
      public int size() {
         return this.delegate.size();
      }

      @Override
      public boolean isEmpty() {
         return this.delegate.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         return this.delegate.contains(o);
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         return this.delegate.containsAll(c);
      }

      @Override
      public Iterator<Entry<K, List<V>>> iterator() {
         final Iterator<? extends Entry<? extends K, ? extends List<? extends V>>> iterator = this.delegate.iterator();
         return new Iterator<Entry<K, List<V>>>() {
            @Override
            public boolean hasNext() {
               return iterator.hasNext();
            }

            public Entry<K, List<V>> next() {
               return new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntry<>((Entry<? extends K, ? extends List<? extends V>>)iterator.next());
            }
         };
      }

      @Override
      public Object[] toArray() {
         Object[] result = this.delegate.toArray();
         this.filterArray(result);
         return result;
      }

      @Override
      public <T> T[] toArray(T[] a) {
         T[] result = (T[])this.delegate.toArray(a);
         this.filterArray(result);
         return result;
      }

      private void filterArray(Object[] result) {
         for (int i = 0; i < result.length; i++) {
            if (result[i] instanceof Entry<?, ?> entry) {
               result[i] = new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntry<>((Entry<?, ? extends List<? extends V>>)entry);
            }
         }
      }

      @Override
      public void forEach(Consumer<? super Entry<K, List<V>>> action) {
         this.delegate
            .forEach(
               e -> action.accept(new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntry<>((Entry<? extends K, ? extends List<? extends V>>)e))
            );
      }

      @Override
      public Stream<Entry<K, List<V>>> stream() {
         return StreamSupport.stream(this.spliterator(), false);
      }

      @Override
      public Stream<Entry<K, List<V>>> parallelStream() {
         return StreamSupport.stream(this.spliterator(), true);
      }

      @Override
      public Spliterator<Entry<K, List<V>>> spliterator() {
         return new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntrySpliterator<>(this.delegate.spliterator());
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof Set<?> that && this.size() == that.size() && this.containsAll(that);
      }

      @Override
      public int hashCode() {
         return this.delegate.hashCode();
      }

      @Override
      public String toString() {
         return this.delegate.toString();
      }

      public boolean add(Entry<K, List<V>> kListEntry) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeIf(Predicate<? super Entry<K, List<V>>> filter) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean addAll(Collection<? extends Entry<K, List<V>>> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException();
      }

      private static class UnmodifiableEntry<K, V> implements Entry<K, List<V>> {
         private final Entry<K, List<V>> delegate;

         public UnmodifiableEntry(Entry<? extends K, ? extends List<? extends V>> delegate) {
            Assert.notNull(delegate, "Delegate must not be null");
            this.delegate = (Entry<K, List<V>>)delegate;
         }

         @Override
         public K getKey() {
            return this.delegate.getKey();
         }

         public List<V> getValue() {
            return Collections.unmodifiableList(this.delegate.getValue());
         }

         public List<V> setValue(List<V> value) {
            throw new UnsupportedOperationException();
         }

         @Override
         public boolean equals(@Nullable Object other) {
            return this == other || other instanceof Entry<?, ?> that && this.getKey().equals(that.getKey()) && this.getValue().equals(that.getValue());
         }

         @Override
         public int hashCode() {
            return this.delegate.hashCode();
         }

         @Override
         public String toString() {
            return this.delegate.toString();
         }
      }

      private static class UnmodifiableEntrySpliterator<K, V> implements Spliterator<Entry<K, List<V>>> {
         private final Spliterator<Entry<K, List<V>>> delegate;

         public UnmodifiableEntrySpliterator(Spliterator<? extends Entry<? extends K, ? extends List<? extends V>>> delegate) {
            this.delegate = (Spliterator<Entry<K, List<V>>>)delegate;
         }

         @Override
         public boolean tryAdvance(Consumer<? super Entry<K, List<V>>> action) {
            return this.delegate
               .tryAdvance(
                  entry -> action.accept(
                     new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntry<>((Entry<? extends K, ? extends List<? extends V>>)entry)
                  )
               );
         }

         @Override
         public void forEachRemaining(Consumer<? super Entry<K, List<V>>> action) {
            this.delegate
               .forEachRemaining(
                  entry -> action.accept(
                     new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntry<>((Entry<? extends K, ? extends List<? extends V>>)entry)
                  )
               );
         }

         @Override
         public @Nullable Spliterator<Entry<K, List<V>>> trySplit() {
            Spliterator<? extends Entry<? extends K, ? extends List<? extends V>>> split = this.delegate.trySplit();
            return split != null ? new UnmodifiableMultiValueMap.UnmodifiableEntrySet.UnmodifiableEntrySpliterator<>(split) : null;
         }

         @Override
         public long estimateSize() {
            return this.delegate.estimateSize();
         }

         @Override
         public long getExactSizeIfKnown() {
            return this.delegate.getExactSizeIfKnown();
         }

         @Override
         public int characteristics() {
            return this.delegate.characteristics();
         }

         @Override
         public boolean hasCharacteristics(int characteristics) {
            return this.delegate.hasCharacteristics(characteristics);
         }

         @Override
         public Comparator<? super Entry<K, List<V>>> getComparator() {
            return this.delegate.getComparator();
         }
      }
   }

   private static class UnmodifiableValueCollection<V> implements Collection<List<V>>, Serializable {
      private static final long serialVersionUID = 5518377583904339588L;
      private final Collection<List<V>> delegate;

      public UnmodifiableValueCollection(Collection<List<V>> delegate) {
         this.delegate = delegate;
      }

      @Override
      public int size() {
         return this.delegate.size();
      }

      @Override
      public boolean isEmpty() {
         return this.delegate.isEmpty();
      }

      @Override
      public boolean contains(Object o) {
         return this.delegate.contains(o);
      }

      @Override
      public boolean containsAll(Collection<?> c) {
         return this.delegate.containsAll(c);
      }

      @Override
      public Object[] toArray() {
         Object[] result = this.delegate.toArray();
         this.filterArray(result);
         return result;
      }

      @Override
      public <T> T[] toArray(T[] a) {
         T[] result = (T[])this.delegate.toArray(a);
         this.filterArray(result);
         return result;
      }

      private void filterArray(Object[] array) {
         for (int i = 0; i < array.length; i++) {
            if (array[i] instanceof List<?> list) {
               array[i] = Collections.unmodifiableList(list);
            }
         }
      }

      @Override
      public Iterator<List<V>> iterator() {
         final Iterator<List<V>> iterator = this.delegate.iterator();
         return new Iterator<List<V>>() {
            @Override
            public boolean hasNext() {
               return iterator.hasNext();
            }

            public List<V> next() {
               return Collections.unmodifiableList(iterator.next());
            }
         };
      }

      @Override
      public void forEach(Consumer<? super List<V>> action) {
         this.delegate.forEach(list -> action.accept(Collections.unmodifiableList((List<? extends V>)list)));
      }

      @Override
      public Spliterator<List<V>> spliterator() {
         return new UnmodifiableMultiValueMap.UnmodifiableValueCollection.UnmodifiableValueSpliterator<>(this.delegate.spliterator());
      }

      @Override
      public Stream<List<V>> stream() {
         return StreamSupport.stream(this.spliterator(), false);
      }

      @Override
      public Stream<List<V>> parallelStream() {
         return StreamSupport.stream(this.spliterator(), true);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || this.delegate.equals(other);
      }

      @Override
      public int hashCode() {
         return this.delegate.hashCode();
      }

      @Override
      public String toString() {
         return this.delegate.toString();
      }

      public boolean add(List<V> ts) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean addAll(Collection<? extends List<V>> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean retainAll(Collection<?> c) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean removeIf(Predicate<? super List<V>> filter) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void clear() {
         throw new UnsupportedOperationException();
      }

      private static class UnmodifiableValueSpliterator<T> implements Spliterator<List<T>> {
         private final Spliterator<List<T>> delegate;

         public UnmodifiableValueSpliterator(Spliterator<List<T>> delegate) {
            this.delegate = delegate;
         }

         @Override
         public boolean tryAdvance(Consumer<? super List<T>> action) {
            return this.delegate.tryAdvance(l -> action.accept(Collections.unmodifiableList((List<? extends T>)l)));
         }

         @Override
         public void forEachRemaining(Consumer<? super List<T>> action) {
            this.delegate.forEachRemaining(l -> action.accept(Collections.unmodifiableList((List<? extends T>)l)));
         }

         @Override
         public @Nullable Spliterator<List<T>> trySplit() {
            Spliterator<List<T>> split = this.delegate.trySplit();
            return split != null ? new UnmodifiableMultiValueMap.UnmodifiableValueCollection.UnmodifiableValueSpliterator<>(split) : null;
         }

         @Override
         public long estimateSize() {
            return this.delegate.estimateSize();
         }

         @Override
         public long getExactSizeIfKnown() {
            return this.delegate.getExactSizeIfKnown();
         }

         @Override
         public int characteristics() {
            return this.delegate.characteristics();
         }

         @Override
         public boolean hasCharacteristics(int characteristics) {
            return this.delegate.hasCharacteristics(characteristics);
         }

         @Override
         public Comparator<? super List<T>> getComparator() {
            return this.delegate.getComparator();
         }
      }
   }
}
