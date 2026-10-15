package org.springframework.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class ConcurrentReferenceHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
   private static final int DEFAULT_INITIAL_CAPACITY = 16;
   private static final float DEFAULT_LOAD_FACTOR = 0.75F;
   private static final int DEFAULT_CONCURRENCY_LEVEL = 16;
   private static final ConcurrentReferenceHashMap.ReferenceType DEFAULT_REFERENCE_TYPE = ConcurrentReferenceHashMap.ReferenceType.SOFT;
   private static final int MAXIMUM_CONCURRENCY_LEVEL = 65536;
   private static final int MAXIMUM_SEGMENT_SIZE = 1073741824;
   private final ConcurrentReferenceHashMap<K, V>.Segment[] segments;
   private final float loadFactor;
   private final ConcurrentReferenceHashMap.ReferenceType referenceType;
   private final int shift;
   private @Nullable Set<Entry<K, V>> entrySet;
   private @Nullable Set<K> keySet;
   private @Nullable Collection<V> values;

   public ConcurrentReferenceHashMap() {
      this(16, 0.75F, 16, DEFAULT_REFERENCE_TYPE);
   }

   public ConcurrentReferenceHashMap(int initialCapacity) {
      this(initialCapacity, 0.75F, 16, DEFAULT_REFERENCE_TYPE);
   }

   public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor) {
      this(initialCapacity, loadFactor, 16, DEFAULT_REFERENCE_TYPE);
   }

   public ConcurrentReferenceHashMap(int initialCapacity, int concurrencyLevel) {
      this(initialCapacity, 0.75F, concurrencyLevel, DEFAULT_REFERENCE_TYPE);
   }

   public ConcurrentReferenceHashMap(int initialCapacity, ConcurrentReferenceHashMap.ReferenceType referenceType) {
      this(initialCapacity, 0.75F, 16, referenceType);
   }

   public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
      this(initialCapacity, loadFactor, concurrencyLevel, DEFAULT_REFERENCE_TYPE);
   }

   public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor, int concurrencyLevel, ConcurrentReferenceHashMap.ReferenceType referenceType) {
      Assert.isTrue(initialCapacity >= 0, "Initial capacity must not be negative");
      Assert.isTrue(loadFactor > 0.0F, "Load factor must be positive");
      Assert.isTrue(concurrencyLevel > 0, "Concurrency level must be positive");
      Assert.notNull(referenceType, "Reference type must not be null");
      this.loadFactor = loadFactor;
      this.shift = calculateShift(concurrencyLevel, 65536);
      int size = 1 << this.shift;
      this.referenceType = referenceType;
      int roundedUpSegmentCapacity = (int)((initialCapacity + size - 1L) / size);
      int initialSize = 1 << calculateShift(roundedUpSegmentCapacity, 1073741824);
      ConcurrentReferenceHashMap<K, V>.Segment[] segments = (ConcurrentReferenceHashMap.Segment[])Array.newInstance(
         ConcurrentReferenceHashMap.Segment.class, size
      );
      int resizeThreshold = (int)(initialSize * this.getLoadFactor());

      for (int i = 0; i < segments.length; i++) {
         segments[i] = new ConcurrentReferenceHashMap.Segment(initialSize, resizeThreshold);
      }

      this.segments = segments;
   }

   protected final float getLoadFactor() {
      return this.loadFactor;
   }

   protected final int getSegmentsSize() {
      return this.segments.length;
   }

   protected final ConcurrentReferenceHashMap<K, V>.Segment getSegment(int index) {
      return this.segments[index];
   }

   protected ConcurrentReferenceHashMap<K, V>.ReferenceManager createReferenceManager() {
      return new ConcurrentReferenceHashMap.ReferenceManager();
   }

   protected int getHash(@Nullable Object o) {
      int hash = o != null ? o.hashCode() : 0;
      hash += hash << 15 ^ -12931;
      hash ^= hash >>> 10;
      hash += hash << 3;
      hash ^= hash >>> 6;
      hash += (hash << 2) + (hash << 14);
      return hash ^ hash >>> 16;
   }

   @Override
   public @Nullable V get(@Nullable Object key) {
      ConcurrentReferenceHashMap.Reference<K, V> ref = this.getReference(key, ConcurrentReferenceHashMap.Restructure.WHEN_NECESSARY);
      ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
      return entry != null ? entry.getValue() : null;
   }

   @Override
   public @Nullable V getOrDefault(@Nullable Object key, @Nullable V defaultValue) {
      ConcurrentReferenceHashMap.Reference<K, V> ref = this.getReference(key, ConcurrentReferenceHashMap.Restructure.WHEN_NECESSARY);
      ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
      return entry != null ? entry.getValue() : defaultValue;
   }

   @Override
   public boolean containsKey(@Nullable Object key) {
      ConcurrentReferenceHashMap.Reference<K, V> ref = this.getReference(key, ConcurrentReferenceHashMap.Restructure.WHEN_NECESSARY);
      ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
      return entry != null && ObjectUtils.nullSafeEquals(entry.getKey(), key);
   }

   protected final ConcurrentReferenceHashMap.@Nullable Reference<K, V> getReference(@Nullable Object key, ConcurrentReferenceHashMap.Restructure restructure) {
      int hash = this.getHash(key);
      return this.getSegmentForHash(hash).getReference(key, hash, restructure);
   }

   @Override
   public @Nullable V put(@Nullable K key, @Nullable V value) {
      return this.put(key, value, true);
   }

   @Override
   public @Nullable V putIfAbsent(@Nullable K key, @Nullable V value) {
      return this.put(key, value, false);
   }

   private @Nullable V put(final @Nullable K key, final @Nullable V value, final boolean overwriteExisting) {
      return this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<V>(
            new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.RESIZE}
         ) {
            @Override
            protected @Nullable V execute(
               ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref,
               ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry,
               ConcurrentReferenceHashMap.@Nullable Entries<V> entries
            ) {
               if (entry != null) {
                  V oldValue = entry.getValue();
                  if (overwriteExisting) {
                     entry.setValue(value);
                  }

                  return oldValue;
               } else {
                  Assert.state(entries != null, "No entries segment");
                  entries.add(value);
                  return null;
               }
            }
         }
      );
   }

   @Override
   public @Nullable V remove(@Nullable Object key) {
      return this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<V>(
            ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_AFTER, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY
         ) {
            @Override
            protected @Nullable V execute(ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref, ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry) {
               if (entry != null) {
                  if (ref != null) {
                     ref.release();
                  }

                  return entry.value;
               } else {
                  return null;
               }
            }
         }
      );
   }

   @Override
   public boolean remove(@Nullable Object key, final @Nullable Object value) {
      Boolean result = this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<Boolean>(
            new ConcurrentReferenceHashMap.TaskOption[]{
               ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_AFTER, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY
            }
         ) {
            protected Boolean execute(ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref, ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry) {
               if (entry != null && ObjectUtils.nullSafeEquals(entry.getValue(), value)) {
                  if (ref != null) {
                     ref.release();
                  }

                  return true;
               } else {
                  return false;
               }
            }
         }
      );
      return Boolean.TRUE.equals(result);
   }

   @Override
   public boolean replace(@Nullable K key, final @Nullable V oldValue, final @Nullable V newValue) {
      Boolean result = this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<Boolean>(
            new ConcurrentReferenceHashMap.TaskOption[]{
               ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY
            }
         ) {
            protected Boolean execute(ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref, ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry) {
               if (entry != null && ObjectUtils.nullSafeEquals(entry.getValue(), oldValue)) {
                  entry.setValue(newValue);
                  return true;
               } else {
                  return false;
               }
            }
         }
      );
      return Boolean.TRUE.equals(result);
   }

   @Override
   public @Nullable V replace(@Nullable K key, final @Nullable V value) {
      return this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<V>(
            new ConcurrentReferenceHashMap.TaskOption[]{
               ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY
            }
         ) {
            @Override
            protected @Nullable V execute(ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref, ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry) {
               if (entry != null) {
                  V oldValue = entry.getValue();
                  entry.setValue(value);
                  return oldValue;
               } else {
                  return null;
               }
            }
         }
      );
   }

   @Override
   public @Nullable V computeIfAbsent(@Nullable K key, Function<@Nullable ? super K, @Nullable ? extends V> mappingFunction) {
      ConcurrentReferenceHashMap.Reference<K, V> ref = this.getReference(key, ConcurrentReferenceHashMap.Restructure.NEVER);
      ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
      return entry != null
         ? entry.getValue()
         : this.doTask(
            key,
            new ConcurrentReferenceHashMap<K, V>.Task<V>(
               new ConcurrentReferenceHashMap.TaskOption[]{
                  ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.RESIZE
               }
            ) {
               @Override
               protected @Nullable V execute(
                  ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref,
                  ConcurrentReferenceHashMap.@Nullable Entry<K, V> entryx,
                  ConcurrentReferenceHashMap.@Nullable Entries<V> entries
               ) {
                  if (entryx != null) {
                     return entryx.getValue();
                  }

                  V value = (V)mappingFunction.apply(key);
                  if (value != null) {
                     Assert.state(entries != null, "No entries segment");
                     entries.add(value);
                  }

                  return value;
               }
            }
         );
   }

   @Override
   public @Nullable V computeIfPresent(@Nullable K key, BiFunction<@Nullable ? super K, @Nullable ? super V, @Nullable ? extends V> remappingFunction) {
      ConcurrentReferenceHashMap.Reference<K, V> ref = this.getReference(key, ConcurrentReferenceHashMap.Restructure.NEVER);
      ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
      return entry == null
         ? null
         : this.doTask(
            key,
            new ConcurrentReferenceHashMap<K, V>.Task<V>(
               new ConcurrentReferenceHashMap.TaskOption[]{
                  ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.RESIZE
               }
            ) {
               @Override
               protected @Nullable V execute(
                  ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref,
                  ConcurrentReferenceHashMap.@Nullable Entry<K, V> entryx,
                  ConcurrentReferenceHashMap.@Nullable Entries<V> entries
               ) {
                  if (entryx != null) {
                     V oldValue = entryx.getValue();
                     V value = (V)remappingFunction.apply(key, oldValue);
                     if (value != null) {
                        entryx.setValue(value);
                        return value;
                     }

                     if (ref != null) {
                        ref.release();
                     }
                  }

                  return null;
               }
            }
         );
   }

   @Override
   public @Nullable V compute(@Nullable K key, BiFunction<@Nullable ? super K, @Nullable ? super V, @Nullable ? extends V> remappingFunction) {
      return this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<V>(
            new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.RESIZE}
         ) {
            @Override
            protected @Nullable V execute(
               ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref,
               ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry,
               ConcurrentReferenceHashMap.@Nullable Entries<V> entries
            ) {
               V oldValue = null;
               if (entry != null) {
                  oldValue = entry.getValue();
               }

               V value = (V)remappingFunction.apply(key, oldValue);
               if (value != null) {
                  if (entry != null) {
                     entry.setValue(value);
                  } else {
                     Assert.state(entries != null, "No entries segment");
                     entries.add(value);
                  }

                  return value;
               } else {
                  if (ref != null) {
                     ref.release();
                  }

                  return null;
               }
            }
         }
      );
   }

   @Override
   public @Nullable V merge(@Nullable K key, @Nullable V value, BiFunction<@Nullable ? super V, @Nullable ? super V, @Nullable ? extends V> remappingFunction) {
      return this.doTask(
         key,
         new ConcurrentReferenceHashMap<K, V>.Task<V>(
            new ConcurrentReferenceHashMap.TaskOption[]{ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE, ConcurrentReferenceHashMap.TaskOption.RESIZE}
         ) {
            @Override
            protected @Nullable V execute(
               ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref,
               ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry,
               ConcurrentReferenceHashMap.@Nullable Entries<V> entries
            ) {
               if (entry != null) {
                  V oldValue = entry.getValue();
                  V newValue = (V)remappingFunction.apply(oldValue, value);
                  if (newValue != null) {
                     entry.setValue(newValue);
                     return newValue;
                  }

                  if (ref != null) {
                     ref.release();
                  }

                  return null;
               } else {
                  Assert.state(entries != null, "No entries segment");
                  entries.add(value);
                  return value;
               }
            }
         }
      );
   }

   @Override
   public void clear() {
      for (ConcurrentReferenceHashMap<K, V>.Segment segment : this.segments) {
         segment.clear();
      }
   }

   public void purgeUnreferencedEntries() {
      for (ConcurrentReferenceHashMap<K, V>.Segment segment : this.segments) {
         segment.restructureIfNecessary(false);
      }
   }

   @Override
   public int size() {
      int size = 0;

      for (ConcurrentReferenceHashMap<K, V>.Segment segment : this.segments) {
         size += segment.getCount();
      }

      return size;
   }

   @Override
   public boolean isEmpty() {
      for (ConcurrentReferenceHashMap<K, V>.Segment segment : this.segments) {
         if (segment.getCount() > 0) {
            return false;
         }
      }

      return true;
   }

   @Override
   public Set<java.util.Map.Entry<K, V>> entrySet() {
      Set<java.util.Map.Entry<K, V>> entrySet = this.entrySet;
      if (entrySet == null) {
         entrySet = new ConcurrentReferenceHashMap.EntrySet();
         this.entrySet = entrySet;
      }

      return entrySet;
   }

   @Override
   public Set<K> keySet() {
      Set<K> keySet = this.keySet;
      if (keySet == null) {
         keySet = new ConcurrentReferenceHashMap.KeySet();
         this.keySet = keySet;
      }

      return keySet;
   }

   @Override
   public Collection<V> values() {
      Collection<V> values = this.values;
      if (values == null) {
         values = new ConcurrentReferenceHashMap.Values();
         this.values = values;
      }

      return values;
   }

   private <T> @Nullable T doTask(@Nullable Object key, ConcurrentReferenceHashMap<K, V>.Task<T> task) {
      int hash = this.getHash(key);
      return this.getSegmentForHash(hash).doTask(hash, key, task);
   }

   private ConcurrentReferenceHashMap<K, V>.Segment getSegmentForHash(int hash) {
      return this.segments[hash >>> 32 - this.shift & this.segments.length - 1];
   }

   protected static int calculateShift(int minimumValue, int maximumValue) {
      int shift = 0;

      for (int value = 1; value < minimumValue && value < maximumValue; shift++) {
         value <<= 1;
      }

      return shift;
   }

   private interface Entries<V> {
      void add(@Nullable V value);
   }

   protected static final class Entry<K, V> implements java.util.Map.Entry<K, V> {
      private final @Nullable K key;
      private volatile @Nullable V value;

      public Entry(@Nullable K key, @Nullable V value) {
         this.key = key;
         this.value = value;
      }

      @Override
      public @Nullable K getKey() {
         return this.key;
      }

      @Override
      public @Nullable V getValue() {
         return this.value;
      }

      @Override
      public @Nullable V setValue(@Nullable V value) {
         V previous = this.value;
         this.value = value;
         return previous;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof java.util.Map.Entry<?, ?> that
               && ObjectUtils.nullSafeEquals(this.getKey(), that.getKey())
               && ObjectUtils.nullSafeEquals(this.getValue(), that.getValue());
      }

      @Override
      public int hashCode() {
         return ObjectUtils.nullSafeHashCode(this.key) ^ ObjectUtils.nullSafeHashCode(this.value);
      }

      @Override
      public String toString() {
         return this.key + "=" + this.value;
      }
   }

   private final class EntryIterator implements Iterator<java.util.Map.Entry<K, V>> {
      private int segmentIndex;
      private int referenceIndex;
      private ConcurrentReferenceHashMap.@Nullable Reference<K, V> @Nullable [] references;
      private ConcurrentReferenceHashMap.@Nullable Reference<K, V> reference;
      private ConcurrentReferenceHashMap.@Nullable Entry<K, V> next;
      private ConcurrentReferenceHashMap.@Nullable Entry<K, V> last;

      public EntryIterator() {
         this.moveToNextSegment();
      }

      @Override
      public boolean hasNext() {
         this.getNextIfNecessary();
         return this.next != null;
      }

      public ConcurrentReferenceHashMap.Entry<K, V> next() {
         this.getNextIfNecessary();
         if (this.next == null) {
            throw new NoSuchElementException();
         }

         this.last = this.next;
         this.next = null;
         return this.last;
      }

      private void getNextIfNecessary() {
         while (this.next == null) {
            this.moveToNextReference();
            if (this.reference == null) {
               return;
            }

            this.next = this.reference.get();
         }
      }

      private void moveToNextReference() {
         if (this.reference != null) {
            this.reference = this.reference.getNext();
         }

         while (this.reference == null && this.references != null) {
            if (this.referenceIndex >= this.references.length) {
               this.moveToNextSegment();
               this.referenceIndex = 0;
            } else {
               this.reference = this.references[this.referenceIndex];
               this.referenceIndex++;
            }
         }
      }

      private void moveToNextSegment() {
         this.reference = null;
         this.references = null;
         if (this.segmentIndex < ConcurrentReferenceHashMap.this.segments.length) {
            this.references = ConcurrentReferenceHashMap.this.segments[this.segmentIndex].references;
            this.segmentIndex++;
         }
      }

      @Override
      public void remove() {
         Assert.state(this.last != null, "No element to remove");
         ConcurrentReferenceHashMap.this.remove(this.last.getKey());
         this.last = null;
      }
   }

   private final class EntrySet extends AbstractSet<java.util.Map.Entry<K, V>> {
      @Override
      public Iterator<java.util.Map.Entry<K, V>> iterator() {
         return ConcurrentReferenceHashMap.this.new EntryIterator();
      }

      @Override
      public boolean contains(@Nullable Object o) {
         if (o instanceof java.util.Map.Entry<?, ?> entry) {
            ConcurrentReferenceHashMap.Reference<K, V> ref = ConcurrentReferenceHashMap.this.getReference(
               entry.getKey(), ConcurrentReferenceHashMap.Restructure.NEVER
            );
            ConcurrentReferenceHashMap.Entry<K, V> otherEntry = ref != null ? ref.get() : null;
            if (otherEntry != null) {
               return ObjectUtils.nullSafeEquals(entry.getValue(), otherEntry.getValue());
            }
         }

         return false;
      }

      @Override
      public boolean remove(Object o) {
         return o instanceof java.util.Map.Entry<?, ?> entry ? ConcurrentReferenceHashMap.this.remove(entry.getKey(), entry.getValue()) : false;
      }

      @Override
      public int size() {
         return ConcurrentReferenceHashMap.this.size();
      }

      @Override
      public void clear() {
         ConcurrentReferenceHashMap.this.clear();
      }

      @Override
      public Spliterator<java.util.Map.Entry<K, V>> spliterator() {
         return Spliterators.spliterator(this, 4097);
      }
   }

   private final class KeyIterator implements Iterator<K> {
      private final Iterator<java.util.Map.Entry<K, V>> iterator = ConcurrentReferenceHashMap.this.entrySet().iterator();

      @Override
      public boolean hasNext() {
         return this.iterator.hasNext();
      }

      @Override
      public void remove() {
         this.iterator.remove();
      }

      @Override
      public K next() {
         return this.iterator.next().getKey();
      }
   }

   private final class KeySet extends AbstractSet<K> {
      @Override
      public Iterator<K> iterator() {
         return ConcurrentReferenceHashMap.this.new KeyIterator();
      }

      @Override
      public int size() {
         return ConcurrentReferenceHashMap.this.size();
      }

      @Override
      public boolean isEmpty() {
         return ConcurrentReferenceHashMap.this.isEmpty();
      }

      @Override
      public void clear() {
         ConcurrentReferenceHashMap.this.clear();
      }

      @Override
      public boolean contains(Object k) {
         return ConcurrentReferenceHashMap.this.containsKey(k);
      }

      @Override
      public Spliterator<K> spliterator() {
         return Spliterators.spliterator(this, 4097);
      }
   }

   protected interface Reference<K, V> {
      ConcurrentReferenceHashMap.@Nullable Entry<K, V> get();

      int getHash();

      ConcurrentReferenceHashMap.@Nullable Reference<K, V> getNext();

      void release();
   }

   protected class ReferenceManager {
      private final ReferenceQueue<ConcurrentReferenceHashMap.Entry<K, V>> queue = new ReferenceQueue<>();

      public ConcurrentReferenceHashMap.Reference<K, V> createReference(
         ConcurrentReferenceHashMap.Entry<K, V> entry, int hash, ConcurrentReferenceHashMap.@Nullable Reference<K, V> next
      ) {
         return ConcurrentReferenceHashMap.this.referenceType == ConcurrentReferenceHashMap.ReferenceType.WEAK
            ? new ConcurrentReferenceHashMap.WeakEntryReference<>(entry, hash, next, this.queue)
            : new ConcurrentReferenceHashMap.SoftEntryReference<>(entry, hash, next, this.queue);
      }

      public ConcurrentReferenceHashMap.@Nullable Reference<K, V> pollForPurge() {
         return (ConcurrentReferenceHashMap.Reference<K, V>)this.queue.poll();
      }
   }

   public enum ReferenceType {
      SOFT,
      WEAK;
   }

   protected enum Restructure {
      WHEN_NECESSARY,
      NEVER;
   }

   protected final class Segment extends ReentrantLock {
      private final ConcurrentReferenceHashMap<K, V>.ReferenceManager referenceManager;
      private final int initialSize;
      private volatile ConcurrentReferenceHashMap.@Nullable Reference<K, V>[] references;
      private final AtomicInteger count = new AtomicInteger();
      private int resizeThreshold;

      public Segment(int initialSize, int resizeThreshold) {
         this.referenceManager = ConcurrentReferenceHashMap.this.createReferenceManager();
         this.initialSize = initialSize;
         this.references = this.createReferenceArray(initialSize);
         this.resizeThreshold = resizeThreshold;
      }

      public ConcurrentReferenceHashMap.Reference<K, V> getReference(Object key, int hash, ConcurrentReferenceHashMap.Restructure restructure) {
         if (restructure == ConcurrentReferenceHashMap.Restructure.WHEN_NECESSARY) {
            this.restructureIfNecessary(false);
         }

         if (this.count.get() == 0) {
            return null;
         }

         ConcurrentReferenceHashMap.Reference<K, V>[] references = this.references;
         int index = this.getIndex(hash, references);
         ConcurrentReferenceHashMap.Reference<K, V> head = references[index];
         return this.findInChain(head, key, hash);
      }

      private <T> @Nullable T doTask(final int hash, final @Nullable Object key, final ConcurrentReferenceHashMap<K, V>.Task<T> task) {
         boolean resize = task.hasOption(ConcurrentReferenceHashMap.TaskOption.RESIZE);
         if (task.hasOption(ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_BEFORE)) {
            this.restructureIfNecessary(resize);
         }

         if (task.hasOption(ConcurrentReferenceHashMap.TaskOption.SKIP_IF_EMPTY) && this.count.get() == 0) {
            return task.execute(null, null, null);
         }

         this.lock();

         try {
            int index = this.getIndex(hash, this.references);
            ConcurrentReferenceHashMap.Reference<K, V> head = this.references[index];
            ConcurrentReferenceHashMap.Reference<K, V> ref = this.findInChain(head, key, hash);
            ConcurrentReferenceHashMap.Entry<K, V> entry = ref != null ? ref.get() : null;
            ConcurrentReferenceHashMap.Entries<V> entries = value -> {
               ConcurrentReferenceHashMap.Entry<K, V> newEntry = new ConcurrentReferenceHashMap.Entry<>((K)key, value);
               ConcurrentReferenceHashMap.Reference<K, V> newReference = this.referenceManager.createReference(newEntry, hash, head);
               this.references[index] = newReference;
               this.count.incrementAndGet();
            };
            return task.execute(ref, entry, entries);
         } finally {
            this.unlock();
            if (task.hasOption(ConcurrentReferenceHashMap.TaskOption.RESTRUCTURE_AFTER)) {
               this.restructureIfNecessary(resize);
            }
         }
      }

      public void clear() {
         if (this.count.get() != 0) {
            this.lock();

            try {
               this.references = this.createReferenceArray(this.initialSize);
               this.resizeThreshold = (int)(this.references.length * ConcurrentReferenceHashMap.this.getLoadFactor());
               this.count.set(0);
            } finally {
               this.unlock();
            }
         }
      }

      void restructureIfNecessary(boolean allowResize) {
         int currCount = this.count.get();
         boolean needsResize = allowResize && currCount > 0 && currCount >= this.resizeThreshold;
         ConcurrentReferenceHashMap.Reference<K, V> ref = this.referenceManager.pollForPurge();
         if (ref != null || needsResize) {
            this.restructure(allowResize, ref);
         }
      }

      private void restructure(boolean allowResize, ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref) {
         this.lock();

         try {
            int expectedCount = this.count.get();
            Set<ConcurrentReferenceHashMap.Reference<K, V>> toPurge = Collections.emptySet();
            if (ref != null) {
               toPurge = new HashSet<>();

               while (ref != null) {
                  toPurge.add(ref);
                  ref = this.referenceManager.pollForPurge();
               }
            }

            expectedCount -= toPurge.size();
            boolean needsResize = expectedCount > 0 && expectedCount >= this.resizeThreshold;
            boolean resizing = false;
            int restructureSize = this.references.length;
            if (allowResize && needsResize && restructureSize < 1073741824) {
               restructureSize <<= 1;
               resizing = true;
            }

            int newCount = 0;
            if (resizing) {
               ConcurrentReferenceHashMap.Reference<K, V>[] restructured = this.createReferenceArray(restructureSize);

               for (ConcurrentReferenceHashMap.Reference<K, V> reference : this.references) {
                  for (ConcurrentReferenceHashMap.Reference<K, V> var20 = reference; var20 != null; var20 = var20.getNext()) {
                     if (!toPurge.contains(var20)) {
                        ConcurrentReferenceHashMap.Entry<K, V> entry = var20.get();
                        if (entry != null) {
                           int index = this.getIndex(var20.getHash(), restructured);
                           restructured[index] = this.referenceManager.createReference(entry, var20.getHash(), restructured[index]);
                           newCount++;
                        }
                     }
                  }
               }

               this.references = restructured;
               this.resizeThreshold = (int)(this.references.length * ConcurrentReferenceHashMap.this.getLoadFactor());
            } else {
               for (int i = 0; i < this.references.length; i++) {
                  ConcurrentReferenceHashMap.Reference<K, V> purgedRef = null;

                  for (ConcurrentReferenceHashMap.Reference<K, V> var19 = this.references[i]; var19 != null; var19 = var19.getNext()) {
                     if (!toPurge.contains(var19)) {
                        ConcurrentReferenceHashMap.Entry<K, V> entry = var19.get();
                        if (entry != null) {
                           purgedRef = this.referenceManager.createReference(entry, var19.getHash(), purgedRef);
                        }

                        newCount++;
                     }
                  }

                  this.references[i] = purgedRef;
               }
            }

            this.count.set(newCount);
         } finally {
            this.unlock();
         }
      }

      private ConcurrentReferenceHashMap.@Nullable Reference<K, V> findInChain(
         ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref, @Nullable Object key, int hash
      ) {
         for (ConcurrentReferenceHashMap.Reference<K, V> currRef = ref; currRef != null; currRef = currRef.getNext()) {
            if (currRef.getHash() == hash) {
               ConcurrentReferenceHashMap.Entry<K, V> entry = currRef.get();
               if (entry != null) {
                  K entryKey = entry.getKey();
                  if (ObjectUtils.nullSafeEquals(entryKey, key)) {
                     return currRef;
                  }
               }
            }
         }

         return null;
      }

      private ConcurrentReferenceHashMap.Reference<K, V>[] createReferenceArray(int size) {
         return new ConcurrentReferenceHashMap.Reference[size];
      }

      private int getIndex(int hash, ConcurrentReferenceHashMap.@Nullable Reference<K, V>[] references) {
         return hash & references.length - 1;
      }

      public int getSize() {
         return this.references.length;
      }

      public int getCount() {
         return this.count.get();
      }
   }

   private static final class SoftEntryReference<K, V>
      extends SoftReference<ConcurrentReferenceHashMap.Entry<K, V>>
      implements ConcurrentReferenceHashMap.Reference<K, V> {
      private final int hash;
      private final ConcurrentReferenceHashMap.@Nullable Reference<K, V> nextReference;

      public SoftEntryReference(
         ConcurrentReferenceHashMap.Entry<K, V> entry,
         int hash,
         ConcurrentReferenceHashMap.@Nullable Reference<K, V> next,
         ReferenceQueue<ConcurrentReferenceHashMap.Entry<K, V>> queue
      ) {
         super(entry, queue);
         this.hash = hash;
         this.nextReference = next;
      }

      @Override
      public int getHash() {
         return this.hash;
      }

      @Override
      public ConcurrentReferenceHashMap.@Nullable Reference<K, V> getNext() {
         return this.nextReference;
      }

      @Override
      public void release() {
         this.enqueue();
      }
   }

   private abstract class Task<T> {
      private final EnumSet<ConcurrentReferenceHashMap.TaskOption> options;

      public Task(ConcurrentReferenceHashMap.TaskOption... options) {
         this.options = options.length == 0 ? EnumSet.noneOf(ConcurrentReferenceHashMap.TaskOption.class) : EnumSet.of(options[0], options);
      }

      public boolean hasOption(ConcurrentReferenceHashMap.TaskOption option) {
         return this.options.contains(option);
      }

      protected @Nullable T execute(
         ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref,
         ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry,
         ConcurrentReferenceHashMap.@Nullable Entries<V> entries
      ) {
         return this.execute(ref, entry);
      }

      protected @Nullable T execute(ConcurrentReferenceHashMap.@Nullable Reference<K, V> ref, ConcurrentReferenceHashMap.@Nullable Entry<K, V> entry) {
         return null;
      }
   }

   private enum TaskOption {
      RESTRUCTURE_BEFORE,
      RESTRUCTURE_AFTER,
      SKIP_IF_EMPTY,
      RESIZE;
   }

   private final class ValueIterator implements Iterator<V> {
      private final Iterator<java.util.Map.Entry<K, V>> iterator = ConcurrentReferenceHashMap.this.entrySet().iterator();

      @Override
      public boolean hasNext() {
         return this.iterator.hasNext();
      }

      @Override
      public void remove() {
         this.iterator.remove();
      }

      @Override
      public V next() {
         return this.iterator.next().getValue();
      }
   }

   private final class Values extends AbstractCollection<V> {
      @Override
      public Iterator<V> iterator() {
         return ConcurrentReferenceHashMap.this.new ValueIterator();
      }

      @Override
      public int size() {
         return ConcurrentReferenceHashMap.this.size();
      }

      @Override
      public boolean isEmpty() {
         return ConcurrentReferenceHashMap.this.isEmpty();
      }

      @Override
      public void clear() {
         ConcurrentReferenceHashMap.this.clear();
      }

      @Override
      public boolean contains(Object v) {
         return ConcurrentReferenceHashMap.this.containsValue(v);
      }

      @Override
      public Spliterator<V> spliterator() {
         return Spliterators.spliterator(this, 4096);
      }
   }

   private static final class WeakEntryReference<K, V>
      extends WeakReference<ConcurrentReferenceHashMap.Entry<K, V>>
      implements ConcurrentReferenceHashMap.Reference<K, V> {
      private final int hash;
      private final ConcurrentReferenceHashMap.@Nullable Reference<K, V> nextReference;

      public WeakEntryReference(
         ConcurrentReferenceHashMap.Entry<K, V> entry,
         int hash,
         ConcurrentReferenceHashMap.@Nullable Reference<K, V> next,
         ReferenceQueue<ConcurrentReferenceHashMap.Entry<K, V>> queue
      ) {
         super(entry, queue);
         this.hash = hash;
         this.nextReference = next;
      }

      @Override
      public int getHash() {
         return this.hash;
      }

      @Override
      public ConcurrentReferenceHashMap.@Nullable Reference<K, V> getNext() {
         return this.nextReference;
      }

      @Override
      public void release() {
         this.enqueue();
      }
   }
}
