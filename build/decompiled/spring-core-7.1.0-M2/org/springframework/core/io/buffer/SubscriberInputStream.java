package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.util.Assert;
import reactor.core.Exceptions;

final class SubscriberInputStream extends InputStream implements Subscriber<DataBuffer> {
   private static final Object READY = new Object();
   private static final DataBuffer DONE = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);
   private static final DataBuffer CLOSED = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);
   private final int prefetch;
   private final int limit;
   private final ReentrantLock lock;
   private final Queue<DataBuffer> queue;
   private final AtomicReference<@Nullable Object> parkedThread = new AtomicReference<>();
   private final AtomicInteger workAmount = new AtomicInteger();
   private volatile boolean closed;
   private int consumed;
   private @Nullable DataBuffer available;
   private @Nullable Subscription subscription;
   private boolean done;
   private @Nullable Throwable error;

   SubscriberInputStream(int demand) {
      this.prefetch = demand;
      this.limit = demand == Integer.MAX_VALUE ? Integer.MAX_VALUE : demand - (demand >> 2);
      this.queue = new ArrayBlockingQueue<>(demand);
      this.lock = new ReentrantLock(false);
   }

   public void onSubscribe(Subscription subscription) {
      if (this.subscription != null) {
         subscription.cancel();
      } else {
         this.subscription = subscription;
         subscription.request(this.prefetch == Integer.MAX_VALUE ? Long.MAX_VALUE : this.prefetch);
      }
   }

   public void onNext(DataBuffer buffer) {
      Assert.notNull(buffer, "DataBuffer must not be null");
      if (this.done) {
         this.discard(buffer);
      } else {
         if (!this.queue.offer(buffer)) {
            this.discard(buffer);
            this.error = new RuntimeException("Buffer overflow");
            this.done = true;
         }

         int previousWorkState = this.addWork();
         if (previousWorkState == Integer.MIN_VALUE) {
            DataBuffer value = this.queue.poll();
            if (value != null) {
               this.discard(value);
            }
         } else {
            if (previousWorkState == 0) {
               this.resume();
            }
         }
      }
   }

   public void onError(Throwable throwable) {
      if (!this.done) {
         this.error = throwable;
         this.done = true;
         if (this.addWork() == 0) {
            this.resume();
         }
      }
   }

   public void onComplete() {
      if (!this.done) {
         this.done = true;
         if (this.addWork() == 0) {
            this.resume();
         }
      }
   }

   int addWork() {
      int produced;
      int nextProduced;
      do {
         produced = this.workAmount.getPlain();
         if (produced == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
         }

         nextProduced = produced == Integer.MAX_VALUE ? 1 : produced + 1;
      } while (!this.workAmount.weakCompareAndSetRelease(produced, nextProduced));

      return produced;
   }

   private void resume() {
      if (this.parkedThread.get() != READY) {
         Object old = this.parkedThread.getAndSet(READY);
         if (old != READY) {
            LockSupport.unpark((Thread)old);
         }
      }
   }

   @Override
   public int read() throws IOException {
      if (!this.lock.tryLock()) {
         if (this.closed) {
            return -1;
         } else {
            throw new ConcurrentModificationException("Concurrent access is not allowed");
         }
      } else {
         try {
            DataBuffer next = this.getNextOrAwait();
            if (next == DONE) {
               this.closed = true;
               this.cleanAndFinalize();
               if (this.error == null) {
                  return -1;
               } else {
                  throw Exceptions.propagate(this.error);
               }
            } else if (next == CLOSED) {
               this.cleanAndFinalize();
               return -1;
            } else {
               return next.read() & 0xFF;
            }
         } catch (Throwable ex) {
            this.closed = true;
            this.requiredSubscriber().cancel();
            this.cleanAndFinalize();
            throw Exceptions.propagate(ex);
         } finally {
            this.lock.unlock();
         }
      }
   }

   @Override
   public int read(byte[] b, int off, int len) throws IOException {
      Objects.checkFromIndexSize(off, len, b.length);
      if (len == 0) {
         return 0;
      }

      if (!this.lock.tryLock()) {
         if (this.closed) {
            return -1;
         } else {
            throw new ConcurrentModificationException("concurrent access is disallowed");
         }
      } else {
         try {
            int j = 0;

            while (j < len) {
               DataBuffer next = this.getNextOrAwait();
               if (next == DONE) {
                  this.cleanAndFinalize();
                  if (this.error == null) {
                     this.closed = true;
                     return j == 0 ? -1 : j;
                  }

                  if (j == 0) {
                     this.closed = true;
                     throw Exceptions.propagate(this.error);
                  }

                  return j;
               }

               if (next == CLOSED) {
                  this.requiredSubscriber().cancel();
                  this.cleanAndFinalize();
                  return -1;
               }

               int initialReadPosition = next.readPosition();
               next.read(b, off + j, Math.min(len - j, next.readableByteCount()));
               j += next.readPosition() - initialReadPosition;
            }

            return len;
         } catch (Throwable ex) {
            this.closed = true;
            this.requiredSubscriber().cancel();
            this.cleanAndFinalize();
            throw Exceptions.propagate(ex);
         } finally {
            this.lock.unlock();
         }
      }
   }

   private DataBuffer getNextOrAwait() {
      if (this.available == null || this.available.readableByteCount() == 0) {
         this.discard(this.available);
         this.available = null;
         int actualWorkAmount = this.workAmount.getAcquire();

         while (true) {
            if (this.closed) {
               return CLOSED;
            }

            boolean done = this.done;
            DataBuffer buffer = this.queue.poll();
            if (buffer != null) {
               int consumed = ++this.consumed;
               this.available = buffer;
               if (consumed == this.limit) {
                  this.consumed = 0;
                  this.requiredSubscriber().request(this.limit);
               }
               break;
            }

            if (done) {
               return DONE;
            }

            actualWorkAmount = this.workAmount.addAndGet(-actualWorkAmount);
            if (actualWorkAmount == 0) {
               this.await();
            }
         }
      }

      return this.available;
   }

   private void cleanAndFinalize() {
      this.discard(this.available);
      this.available = null;

      int workAmount;
      do {
         workAmount = this.workAmount.getPlain();

         DataBuffer value;
         while ((value = this.queue.poll()) != null) {
            this.discard(value);
         }
      } while (!this.workAmount.weakCompareAndSetPlain(workAmount, Integer.MIN_VALUE));
   }

   @Override
   public void close() throws IOException {
      if (!this.closed) {
         this.closed = true;
         if (!this.lock.tryLock()) {
            if (this.addWork() == 0) {
               this.resume();
            }
         } else {
            try {
               this.requiredSubscriber().cancel();
               this.cleanAndFinalize();
            } finally {
               this.lock.unlock();
            }
         }
      }
   }

   private Subscription requiredSubscriber() {
      Assert.state(this.subscription != null, "Subscriber must be subscribed to use InputStream");
      return this.subscription;
   }

   private void discard(@Nullable DataBuffer buffer) {
      DataBufferUtils.release(buffer);
   }

   private void await() {
      Thread toUnpark = Thread.currentThread();

      while (true) {
         Object current = this.parkedThread.get();
         if (current == READY) {
            this.parkedThread.lazySet(null);
            return;
         }

         if (current != null && current != toUnpark) {
            throw new IllegalStateException("Only one (Virtual)Thread can await!");
         }

         if (this.parkedThread.compareAndSet(null, toUnpark)) {
            LockSupport.park();
         }
      }
   }
}
