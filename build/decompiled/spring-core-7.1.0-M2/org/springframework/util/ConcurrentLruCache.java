package org.springframework.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class ConcurrentLruCache<K, V> {
   private final int capacity;
   private final AtomicInteger currentSize = new AtomicInteger();
   private final ConcurrentMap<K, ConcurrentLruCache.Node<K, V>> cache;
   private final Function<K, V> generator;
   private final ConcurrentLruCache.ReadOperations<K, V> readOperations;
   private final ConcurrentLruCache.WriteOperations writeOperations;
   private final Lock evictionLock = new ReentrantLock();
   private final ConcurrentLruCache.EvictionQueue<K, V> evictionQueue = new ConcurrentLruCache.EvictionQueue<>();
   private final AtomicReference<ConcurrentLruCache.DrainStatus> drainStatus = new AtomicReference<>(ConcurrentLruCache.DrainStatus.IDLE);

   public ConcurrentLruCache(int capacity, Function<K, V> generator) {
      this(capacity, generator, 16);
   }

   private ConcurrentLruCache(int capacity, Function<K, V> generator, int concurrencyLevel) {
      Assert.isTrue(capacity >= 0, "Capacity must be >= 0");
      this.capacity = capacity;
      this.cache = new ConcurrentHashMap<>(16, 0.75F, concurrencyLevel);
      this.generator = generator;
      this.readOperations = new ConcurrentLruCache.ReadOperations<>(this.evictionQueue);
      this.writeOperations = new ConcurrentLruCache.WriteOperations();
   }

   public V get(K key) {
      if (this.capacity == 0) {
         return this.generator.apply(key);
      } else {
         ConcurrentLruCache.Node<K, V> node = this.cache.get(key);
         if (node == null) {
            V value = this.generator.apply(key);
            this.put(key, value);
            return value;
         } else {
            this.processRead(node);
            return node.getValue();
         }
      }
   }

   private void put(K key, V value) {
      Assert.notNull(key, "key must not be null");
      Assert.notNull(value, "value must not be null");
      ConcurrentLruCache.CacheEntry<V> cacheEntry = new ConcurrentLruCache.CacheEntry<>(value, ConcurrentLruCache.CacheEntryState.ACTIVE);
      ConcurrentLruCache.Node<K, V> node = new ConcurrentLruCache.Node<>(key, cacheEntry);
      ConcurrentLruCache.Node<K, V> prior = this.cache.putIfAbsent(node.key, node);
      if (prior == null) {
         this.processWrite(new ConcurrentLruCache.AddTask(node));
      } else {
         this.processRead(prior);
      }
   }

   private void processRead(ConcurrentLruCache.Node<K, V> node) {
      boolean drainRequested = this.readOperations.recordRead(node);
      ConcurrentLruCache.DrainStatus status = this.drainStatus.get();
      if (status.shouldDrainBuffers(drainRequested)) {
         this.drainOperations();
      }
   }

   private void processWrite(Runnable task) {
      this.writeOperations.add(task);
      this.drainStatus.lazySet(ConcurrentLruCache.DrainStatus.REQUIRED);
      this.drainOperations();
   }

   private void drainOperations() {
      if (this.evictionLock.tryLock()) {
         try {
            this.drainStatus.lazySet(ConcurrentLruCache.DrainStatus.PROCESSING);
            this.readOperations.drain();
            this.writeOperations.drain();
         } finally {
            this.drainStatus.compareAndSet(ConcurrentLruCache.DrainStatus.PROCESSING, ConcurrentLruCache.DrainStatus.IDLE);
            this.evictionLock.unlock();
         }
      }
   }

   public int capacity() {
      return this.capacity;
   }

   @Deprecated(since = "6.0")
   public int sizeLimit() {
      return this.capacity;
   }

   public int size() {
      return this.cache.size();
   }

   public void clear() {
      this.evictionLock.lock();

      try {
         for (ConcurrentLruCache.Node<K, V> node : this.cache.values()) {
            this.cache.remove(node.key, node);
            this.evictionQueue.remove(node);
            this.markAsRemoved(node);
         }

         this.readOperations.clear();
         this.writeOperations.drainAll();
      } finally {
         this.evictionLock.unlock();
      }
   }

   private void markAsRemoved(ConcurrentLruCache.Node<K, V> node) {
      ConcurrentLruCache.CacheEntry<V> current;
      ConcurrentLruCache.CacheEntry<V> removed;
      do {
         current = node.get();
         if (current.state == ConcurrentLruCache.CacheEntryState.REMOVED) {
            return;
         }

         removed = new ConcurrentLruCache.CacheEntry<>(current.value, ConcurrentLruCache.CacheEntryState.REMOVED);
      } while (!node.compareAndSet(current, removed));

      this.currentSize.lazySet(this.currentSize.get() - 1);
   }

   public boolean contains(K key) {
      return this.cache.containsKey(key);
   }

   public boolean remove(K key) {
      ConcurrentLruCache.Node<K, V> node = this.cache.remove(key);
      if (node == null) {
         return false;
      }

      this.markForRemoval(node);
      this.processWrite(new ConcurrentLruCache.RemovalTask(node));
      return true;
   }

   private void markForRemoval(ConcurrentLruCache.Node<K, V> node) {
      ConcurrentLruCache.CacheEntry<V> current;
      ConcurrentLruCache.CacheEntry<V> pendingRemoval;
      do {
         current = node.get();
         if (!current.isActive()) {
            return;
         }

         pendingRemoval = new ConcurrentLruCache.CacheEntry<>(current.value, ConcurrentLruCache.CacheEntryState.PENDING_REMOVAL);
      } while (!node.compareAndSet(current, pendingRemoval));
   }

   private final class AddTask implements Runnable {
      final ConcurrentLruCache.Node<K, V> node;

      AddTask(ConcurrentLruCache.Node<K, V> node) {
         this.node = node;
      }

      @Override
      public void run() {
         ConcurrentLruCache.this.currentSize.lazySet(ConcurrentLruCache.this.currentSize.get() + 1);
         if (this.node.get().isActive()) {
            ConcurrentLruCache.this.evictionQueue.add(this.node);
            this.evictEntries();
         }
      }

      private void evictEntries() {
         while (ConcurrentLruCache.this.currentSize.get() > ConcurrentLruCache.this.capacity) {
            ConcurrentLruCache.Node<K, V> node = ConcurrentLruCache.this.evictionQueue.poll();
            if (node == null) {
               return;
            }

            ConcurrentLruCache.this.cache.remove(node.key, node);
            ConcurrentLruCache.this.markAsRemoved(node);
         }
      }
   }

   private record CacheEntry<V>(V value, ConcurrentLruCache.CacheEntryState state) {
      boolean isActive() {
         return this.state == ConcurrentLruCache.CacheEntryState.ACTIVE;
      }
   }

   private enum CacheEntryState {
      ACTIVE,
      PENDING_REMOVAL,
      REMOVED;
   }

   private enum DrainStatus {
      IDLE {
         @Override
         boolean shouldDrainBuffers(boolean delayable) {
            return !delayable;
         }
      },
      REQUIRED {
         @Override
         boolean shouldDrainBuffers(boolean delayable) {
            return true;
         }
      },
      PROCESSING {
         @Override
         boolean shouldDrainBuffers(boolean delayable) {
            return false;
         }
      };

      abstract boolean shouldDrainBuffers(boolean delayable);
   }

   private static final class EvictionQueue<K, V> {
      ConcurrentLruCache.@Nullable Node<K, V> first;
      ConcurrentLruCache.@Nullable Node<K, V> last;

      ConcurrentLruCache.@Nullable Node<K, V> poll() {
         if (this.first == null) {
            return null;
         }

         ConcurrentLruCache.Node<K, V> f = this.first;
         ConcurrentLruCache.Node<K, V> next = f.getNext();
         f.setNext(null);
         this.first = next;
         if (next == null) {
            this.last = null;
         } else {
            next.setPrevious(null);
         }

         return f;
      }

      void add(ConcurrentLruCache.Node<K, V> e) {
         if (!this.contains(e)) {
            this.linkLast(e);
         }
      }

      private boolean contains(ConcurrentLruCache.Node<K, V> e) {
         return e.getPrevious() != null || e.getNext() != null || e == this.first;
      }

      private void linkLast(ConcurrentLruCache.Node<K, V> e) {
         ConcurrentLruCache.Node<K, V> l = this.last;
         this.last = e;
         if (l == null) {
            this.first = e;
         } else {
            l.setNext(e);
            e.setPrevious(l);
         }
      }

      private void unlink(ConcurrentLruCache.Node<K, V> e) {
         ConcurrentLruCache.Node<K, V> prev = e.getPrevious();
         ConcurrentLruCache.Node<K, V> next = e.getNext();
         if (prev == null) {
            this.first = next;
         } else {
            prev.setNext(next);
            e.setPrevious(null);
         }

         if (next == null) {
            this.last = prev;
         } else {
            next.setPrevious(prev);
            e.setNext(null);
         }
      }

      void moveToBack(ConcurrentLruCache.Node<K, V> e) {
         if (this.contains(e) && e != this.last) {
            this.unlink(e);
            this.linkLast(e);
         }
      }

      void remove(ConcurrentLruCache.Node<K, V> e) {
         if (this.contains(e)) {
            this.unlink(e);
         }
      }
   }

   private static final class Node<K, V> extends AtomicReference<ConcurrentLruCache.CacheEntry<V>> {
      final K key;
      ConcurrentLruCache.@Nullable Node<K, V> prev;
      ConcurrentLruCache.@Nullable Node<K, V> next;

      Node(K key, ConcurrentLruCache.CacheEntry<V> cacheEntry) {
         super(cacheEntry);
         this.key = key;
      }

      public ConcurrentLruCache.@Nullable Node<K, V> getPrevious() {
         return this.prev;
      }

      public void setPrevious(ConcurrentLruCache.@Nullable Node<K, V> prev) {
         this.prev = prev;
      }

      public ConcurrentLruCache.@Nullable Node<K, V> getNext() {
         return this.next;
      }

      public void setNext(ConcurrentLruCache.@Nullable Node<K, V> next) {
         this.next = next;
      }

      V getValue() {
         return this.get().value;
      }
   }

   private static final class ReadOperations<K, V> {
      private static final int BUFFER_COUNT = 4;
      private static final int BUFFERS_MASK = 3;
      private static final int MAX_PENDING_OPERATIONS = 32;
      private static final int MAX_DRAIN_COUNT = 64;
      private static final int BUFFER_SIZE = 128;
      private static final int BUFFER_INDEX_MASK = 127;
      private final AtomicLongArray recordedCount = new AtomicLongArray(4);
      private final long[] readCount = new long[4];
      private final AtomicLongArray processedCount = new AtomicLongArray(4);
      private final AtomicReferenceArray<ConcurrentLruCache.@Nullable Node<K, V>>[] buffers = new AtomicReferenceArray[4];
      private final ConcurrentLruCache.EvictionQueue<K, V> evictionQueue;

      ReadOperations(ConcurrentLruCache.EvictionQueue<K, V> evictionQueue) {
         this.evictionQueue = evictionQueue;

         for (int i = 0; i < 4; i++) {
            this.buffers[i] = new AtomicReferenceArray<>(128);
         }
      }

      private static int getBufferIndex() {
         return (int)Thread.currentThread().getId() & 3;
      }

      boolean recordRead(ConcurrentLruCache.Node<K, V> node) {
         int bufferIndex = getBufferIndex();
         long writeCount = this.recordedCount.get(bufferIndex);
         this.recordedCount.lazySet(bufferIndex, writeCount + 1L);
         int index = (int)(writeCount & 127L);
         this.buffers[bufferIndex].lazySet(index, node);
         long pending = writeCount - this.processedCount.get(bufferIndex);
         return pending < 32L;
      }

      void drain() {
         int start = (int)Thread.currentThread().getId();
         int end = start + 4;

         for (int i = start; i < end; i++) {
            this.drainReadBuffer(i & 3);
         }
      }

      void clear() {
         for (int i = 0; i < 4; i++) {
            AtomicReferenceArray<ConcurrentLruCache.Node<K, V>> buffer = this.buffers[i];

            for (int j = 0; j < 128; j++) {
               buffer.lazySet(j, null);
            }
         }
      }

      private void drainReadBuffer(int bufferIndex) {
         long writeCount = this.recordedCount.get(bufferIndex);

         for (int i = 0; i < 64; i++) {
            int index = (int)(this.readCount[bufferIndex] & 127L);
            AtomicReferenceArray<ConcurrentLruCache.Node<K, V>> buffer = this.buffers[bufferIndex];
            ConcurrentLruCache.Node<K, V> node = buffer.get(index);
            if (node == null) {
               break;
            }

            buffer.lazySet(index, null);
            this.evictionQueue.moveToBack(node);
            this.readCount[bufferIndex]++;
         }

         this.processedCount.lazySet(bufferIndex, writeCount);
      }
   }

   private final class RemovalTask implements Runnable {
      final ConcurrentLruCache.Node<K, V> node;

      RemovalTask(ConcurrentLruCache.Node<K, V> node) {
         this.node = node;
      }

      @Override
      public void run() {
         ConcurrentLruCache.this.evictionQueue.remove(this.node);
         ConcurrentLruCache.this.markAsRemoved(this.node);
      }
   }

   private static final class WriteOperations {
      private static final int DRAIN_THRESHOLD = 16;
      private final Queue<Runnable> operations = new ConcurrentLinkedQueue<>();

      public void add(Runnable task) {
         this.operations.add(task);
      }

      public void drain() {
         for (int i = 0; i < 16; i++) {
            Runnable task = this.operations.poll();
            if (task == null) {
               break;
            }

            task.run();
         }
      }

      public void drainAll() {
         Runnable task;
         while ((task = this.operations.poll()) != null) {
            task.run();
         }
      }
   }
}
