package org.springframework.util;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class LinkedCaseInsensitiveMap<V> implements Map<String, V>, Serializable, Cloneable {
   private static final long serialVersionUID = -1797561627545787622L;
   private final LinkedHashMap<String, V> targetMap;
   private final HashMap<String, String> caseInsensitiveKeys;
   private final Locale locale;
   private transient volatile @Nullable Set<String> keySet;
   private transient volatile @Nullable Collection<V> values;
   private transient volatile @Nullable Set<Entry<String, V>> entrySet;

   public LinkedCaseInsensitiveMap() {
      this((Locale)null);
   }

   public LinkedCaseInsensitiveMap(@Nullable Locale locale) {
      this(12, locale);
   }

   public LinkedCaseInsensitiveMap(int expectedSize) {
      this(expectedSize, null);
   }

   public LinkedCaseInsensitiveMap(int expectedSize, @Nullable Locale locale) {
      this.targetMap = new LinkedHashMap<String, V>((int)(expectedSize / 0.75F), 0.75F) {
         @Override
         public boolean containsKey(Object key) {
            return LinkedCaseInsensitiveMap.this.containsKey(key);
         }

         @Override
         protected boolean removeEldestEntry(Entry<String, V> eldest) {
            boolean doRemove = LinkedCaseInsensitiveMap.this.removeEldestEntry(eldest);
            if (doRemove) {
               LinkedCaseInsensitiveMap.this.removeCaseInsensitiveKey(eldest.getKey());
            }

            return doRemove;
         }
      };
      this.caseInsensitiveKeys = CollectionUtils.newHashMap(expectedSize);
      this.locale = locale != null ? locale : Locale.getDefault();
   }

   private LinkedCaseInsensitiveMap(LinkedCaseInsensitiveMap<V> other) {
      this.targetMap = (LinkedHashMap<String, V>)other.targetMap.clone();
      this.caseInsensitiveKeys = (HashMap<String, String>)other.caseInsensitiveKeys.clone();
      this.locale = other.locale;
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
      return key instanceof String string && this.caseInsensitiveKeys.containsKey(this.convertKey(string));
   }

   @Override
   public boolean containsValue(Object value) {
      return this.targetMap.containsValue(value);
   }

   @Override
   public @Nullable V get(Object key) {
      if (key instanceof String string) {
         String caseInsensitiveKey = this.caseInsensitiveKeys.get(this.convertKey(string));
         if (caseInsensitiveKey != null) {
            return this.targetMap.get(caseInsensitiveKey);
         }
      }

      return null;
   }

   @Override
   public @Nullable V getOrDefault(Object key, V defaultValue) {
      if (key instanceof String string) {
         String caseInsensitiveKey = this.caseInsensitiveKeys.get(this.convertKey(string));
         if (caseInsensitiveKey != null) {
            return this.targetMap.get(caseInsensitiveKey);
         }
      }

      return defaultValue;
   }

   public @Nullable V put(String key, @Nullable V value) {
      String oldKey = this.caseInsensitiveKeys.put(this.convertKey(key), key);
      V oldKeyValue = null;
      if (oldKey != null && !oldKey.equals(key)) {
         oldKeyValue = this.targetMap.remove(oldKey);
      }

      V oldValue = this.targetMap.put(key, value);
      return oldKeyValue != null ? oldKeyValue : oldValue;
   }

   @Override
   public void putAll(Map<? extends String, ? extends V> map) {
      if (!map.isEmpty()) {
         map.forEach(this::put);
      }
   }

   public @Nullable V putIfAbsent(String key, @Nullable V value) {
      String oldKey = this.caseInsensitiveKeys.putIfAbsent(this.convertKey(key), key);
      if (oldKey != null) {
         V oldKeyValue = this.targetMap.get(oldKey);
         if (oldKeyValue != null) {
            return oldKeyValue;
         }

         key = oldKey;
      }

      return this.targetMap.putIfAbsent(key, value);
   }

   public @Nullable V computeIfAbsent(String key, Function<? super String, ? extends V> mappingFunction) {
      String oldKey = this.caseInsensitiveKeys.putIfAbsent(this.convertKey(key), key);
      if (oldKey != null) {
         V oldKeyValue = this.targetMap.get(oldKey);
         if (oldKeyValue != null) {
            return oldKeyValue;
         }

         key = oldKey;
      }

      return this.targetMap.computeIfAbsent(key, mappingFunction);
   }

   @Override
   public @Nullable V remove(Object key) {
      if (key instanceof String string) {
         String caseInsensitiveKey = this.removeCaseInsensitiveKey(string);
         if (caseInsensitiveKey != null) {
            return this.targetMap.remove(caseInsensitiveKey);
         }
      }

      return null;
   }

   @Override
   public void clear() {
      this.caseInsensitiveKeys.clear();
      this.targetMap.clear();
   }

   @Override
   public Set<String> keySet() {
      Set<String> keySet = this.keySet;
      if (keySet == null) {
         keySet = new LinkedCaseInsensitiveMap.KeySet(this.targetMap.keySet());
         this.keySet = keySet;
      }

      return keySet;
   }

   @Override
   public Collection<V> values() {
      Collection<V> values = this.values;
      if (values == null) {
         values = new LinkedCaseInsensitiveMap.Values(this.targetMap.values());
         this.values = values;
      }

      return values;
   }

   @Override
   public Set<Entry<String, V>> entrySet() {
      Set<Entry<String, V>> entrySet = this.entrySet;
      if (entrySet == null) {
         entrySet = new LinkedCaseInsensitiveMap.EntrySet(this.targetMap.entrySet());
         this.entrySet = entrySet;
      }

      return entrySet;
   }

   @Override
   public void forEach(BiConsumer<? super String, ? super V> action) {
      this.targetMap.forEach(action);
   }

   public LinkedCaseInsensitiveMap<V> clone() {
      return new LinkedCaseInsensitiveMap<>(this);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || this.targetMap.equals(other);
   }

   @Override
   public int hashCode() {
      return this.targetMap.hashCode();
   }

   @Override
   public String toString() {
      return this.targetMap.toString();
   }

   public Locale getLocale() {
      return this.locale;
   }

   protected String convertKey(String key) {
      return key.toLowerCase(this.getLocale());
   }

   protected boolean removeEldestEntry(Entry<String, V> eldest) {
      return false;
   }

   private @Nullable String removeCaseInsensitiveKey(String key) {
      return this.caseInsensitiveKeys.remove(this.convertKey(key));
   }

   private abstract class EntryIterator<T> implements Iterator<T> {
      private final Iterator<Entry<String, V>> delegate = LinkedCaseInsensitiveMap.this.targetMap.entrySet().iterator();
      private @Nullable Entry<String, V> last;

      public EntryIterator() {
      }

      protected Entry<String, V> nextEntry() {
         Entry<String, V> entry = this.delegate.next();
         this.last = entry;
         return entry;
      }

      @Override
      public boolean hasNext() {
         return this.delegate.hasNext();
      }

      @Override
      public void remove() {
         this.delegate.remove();
         if (this.last != null) {
            LinkedCaseInsensitiveMap.this.removeCaseInsensitiveKey(this.last.getKey());
            this.last = null;
         }
      }
   }

   private class EntrySet extends AbstractSet<Entry<String, V>> {
      private final Set<Entry<String, V>> delegate;

      EntrySet(Set<Entry<String, V>> delegate) {
         this.delegate = delegate;
      }

      @Override
      public int size() {
         return this.delegate.size();
      }

      @Override
      public boolean contains(Object o) {
         if (o instanceof Entry<?, ?> entry) {
            Object key = entry.getKey();
            if (!LinkedCaseInsensitiveMap.this.containsKey(key)) {
               return false;
            }

            V value = LinkedCaseInsensitiveMap.this.get(key);
            return ObjectUtils.nullSafeEquals(value, entry.getValue());
         } else {
            return false;
         }
      }

      @Override
      public Iterator<Entry<String, V>> iterator() {
         return LinkedCaseInsensitiveMap.this.new EntrySetIterator();
      }

      @Override
      public boolean remove(Object o) {
         if (o instanceof Entry<?, ?> entry) {
            Object key = entry.getKey();
            if (!LinkedCaseInsensitiveMap.this.containsKey(key)) {
               return false;
            }

            V value = LinkedCaseInsensitiveMap.this.get(key);
            if (!ObjectUtils.nullSafeEquals(value, entry.getValue())) {
               return false;
            }

            LinkedCaseInsensitiveMap.this.remove(key);
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void clear() {
         LinkedCaseInsensitiveMap.this.clear();
      }

      @Override
      public Spliterator<Entry<String, V>> spliterator() {
         return this.delegate.spliterator();
      }

      @Override
      public void forEach(Consumer<? super Entry<String, V>> action) {
         this.delegate.forEach(action);
      }
   }

   private class EntrySetIterator extends LinkedCaseInsensitiveMap<V>.EntryIterator<Entry<String, V>> {
      public Entry<String, V> next() {
         return this.nextEntry();
      }
   }

   private class KeySet extends AbstractSet<String> {
      private final Set<String> delegate;

      KeySet(Set<String> delegate) {
         this.delegate = delegate;
      }

      @Override
      public int size() {
         return this.delegate.size();
      }

      @Override
      public boolean contains(Object o) {
         return this.delegate.contains(o);
      }

      @Override
      public Iterator<String> iterator() {
         return LinkedCaseInsensitiveMap.this.new KeySetIterator();
      }

      @Override
      public boolean remove(Object o) {
         return LinkedCaseInsensitiveMap.this.remove(o) != null;
      }

      @Override
      public void clear() {
         LinkedCaseInsensitiveMap.this.clear();
      }

      @Override
      public Spliterator<String> spliterator() {
         return this.delegate.spliterator();
      }

      @Override
      public void forEach(Consumer<? super String> action) {
         this.delegate.forEach(action);
      }
   }

   private class KeySetIterator extends LinkedCaseInsensitiveMap<V>.EntryIterator<String> {
      public String next() {
         return (String)this.nextEntry().getKey();
      }
   }

   private class Values extends AbstractCollection<V> {
      private final Collection<V> delegate;

      Values(Collection<V> delegate) {
         this.delegate = delegate;
      }

      @Override
      public int size() {
         return this.delegate.size();
      }

      @Override
      public boolean contains(Object o) {
         return this.delegate.contains(o);
      }

      @Override
      public Iterator<V> iterator() {
         return LinkedCaseInsensitiveMap.this.new ValuesIterator();
      }

      @Override
      public void clear() {
         LinkedCaseInsensitiveMap.this.clear();
      }

      @Override
      public Spliterator<V> spliterator() {
         return this.delegate.spliterator();
      }

      @Override
      public void forEach(Consumer<? super V> action) {
         this.delegate.forEach(action);
      }
   }

   private class ValuesIterator extends LinkedCaseInsensitiveMap<V>.EntryIterator<V> {
      @Override
      public V next() {
         return (V)this.nextEntry().getValue();
      }
   }
}
