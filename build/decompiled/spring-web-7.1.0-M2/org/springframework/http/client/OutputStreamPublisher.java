package org.springframework.http.client;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class OutputStreamPublisher<T> implements Publisher<T> {
   private static final int DEFAULT_CHUNK_SIZE = 1024;
   private final OutputStreamPublisher.OutputStreamHandler outputStreamHandler;
   private final OutputStreamPublisher.ByteMapper<T> byteMapper;
   private final Executor executor;
   private final int chunkSize;

   OutputStreamPublisher(
      OutputStreamPublisher.OutputStreamHandler outputStreamHandler,
      OutputStreamPublisher.ByteMapper<T> byteMapper,
      Executor executor,
      @Nullable Integer chunkSize
   ) {
      Assert.notNull(outputStreamHandler, "OutputStreamHandler must not be null");
      Assert.notNull(byteMapper, "ByteMapper must not be null");
      Assert.notNull(executor, "Executor must not be null");
      Assert.isTrue(chunkSize == null || chunkSize > 0, "ChunkSize must be larger than 0");
      this.outputStreamHandler = outputStreamHandler;
      this.byteMapper = byteMapper;
      this.executor = executor;
      this.chunkSize = chunkSize != null ? chunkSize : 1024;
   }

   @Override
   public void subscribe(Subscriber<? super T> subscriber) {
      Objects.requireNonNull(subscriber, "Subscriber must not be null");
      OutputStreamPublisher.OutputStreamSubscription<T> subscription = new OutputStreamPublisher.OutputStreamSubscription<>(
         subscriber, this.outputStreamHandler, this.byteMapper, this.chunkSize
      );
      subscriber.onSubscribe(subscription);
      this.executor.execute(subscription::invokeHandler);
   }

   public interface ByteMapper<T> {
      T map(int b);

      T map(byte[] b, int off, int len);
   }

   @FunctionalInterface
   public interface OutputStreamHandler {
      void handle(OutputStream outputStream) throws Exception;
   }

   private static final class OutputStreamSubscription<T> extends OutputStream implements Subscription {
      private static final Object READY = new Object();
      private final Subscriber<? super T> actual;
      private final OutputStreamPublisher.OutputStreamHandler outputStreamHandler;
      private final OutputStreamPublisher.ByteMapper<T> byteMapper;
      private final int chunkSize;
      private final AtomicLong requested = new AtomicLong();
      private final AtomicReference<@Nullable Object> parkedThread = new AtomicReference<>();
      private volatile @Nullable Throwable error;
      private long produced;

      OutputStreamSubscription(
         Subscriber<? super T> actual,
         OutputStreamPublisher.OutputStreamHandler outputStreamHandler,
         OutputStreamPublisher.ByteMapper<T> byteMapper,
         int chunkSize
      ) {
         this.actual = actual;
         this.outputStreamHandler = outputStreamHandler;
         this.byteMapper = byteMapper;
         this.chunkSize = chunkSize;
      }

      @Override
      public void write(int b) throws IOException {
         this.checkDemandAndAwaitIfNeeded();
         T next = this.byteMapper.map(b);
         this.actual.onNext(next);
         this.produced++;
      }

      @Override
      public void write(byte[] b) throws IOException {
         this.write(b, 0, b.length);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
         this.checkDemandAndAwaitIfNeeded();
         T next = this.byteMapper.map(b, off, len);
         this.actual.onNext(next);
         this.produced++;
      }

      private void checkDemandAndAwaitIfNeeded() throws IOException {
         long r = this.requested.get();
         if (!isTerminated(r) && !isCancelled(r)) {
            long p = this.produced;
            if (p == r) {
               if (p > 0L) {
                  r = this.tryProduce(p);
                  this.produced = 0L;
               }

               while (!isTerminated(r) && !isCancelled(r)) {
                  if (r != 0L) {
                     return;
                  }

                  this.await();
                  r = this.requested.get();
               }

               throw new IOException("Subscription has been terminated");
            }
         } else {
            throw new IOException("Subscription has been terminated");
         }
      }

      private void invokeHandler() {
         try (OutputStream outputStream = new BufferedOutputStream(this, this.chunkSize)) {
            this.outputStreamHandler.handle(outputStream);
         } catch (Exception ex) {
            long previousState = this.tryTerminate();
            if (isCancelled(previousState)) {
               return;
            }

            if (isTerminated(previousState)) {
               Throwable error = this.error;
               if (error != null) {
                  this.actual.onError(error);
                  return;
               }
            }

            this.actual.onError(ex);
            return;
         }

         long previousState = this.tryTerminate();
         if (!isCancelled(previousState)) {
            if (isTerminated(previousState)) {
               Throwable error = this.error;
               if (error != null) {
                  this.actual.onError(error);
                  return;
               }
            }

            this.actual.onComplete();
         }
      }

      @Override
      public void request(long n) {
         if (n <= 0L) {
            this.error = new IllegalArgumentException("request should be a positive number");
            long previousState = this.tryTerminate();
            if (!isTerminated(previousState) && !isCancelled(previousState)) {
               if (previousState <= 0L) {
                  this.resume();
               }
            }
         } else {
            if (this.addCap(n) == 0L) {
               this.resume();
            }
         }
      }

      @Override
      public void cancel() {
         long previousState = this.tryCancel();
         if (!isCancelled(previousState) && previousState <= 0L) {
            this.resume();
         }
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

      private void resume() {
         if (this.parkedThread.get() != READY) {
            Object old = this.parkedThread.getAndSet(READY);
            if (old != READY) {
               LockSupport.unpark((Thread)old);
            }
         }
      }

      private long tryCancel() {
         long r;
         do {
            r = this.requested.get();
            if (isCancelled(r)) {
               return r;
            }
         } while (!this.requested.compareAndSet(r, Long.MIN_VALUE));

         return r;
      }

      private long tryTerminate() {
         while (true) {
            long r = this.requested.get();
            if (!isCancelled(r) && !isTerminated(r)) {
               if (!this.requested.compareAndSet(r, -1L)) {
                  continue;
               }

               return r;
            }

            return r;
         }
      }

      private long tryProduce(long n) {
         while (true) {
            long current = this.requested.get();
            if (!isTerminated(current) && !isCancelled(current)) {
               if (current == Long.MAX_VALUE) {
                  return Long.MAX_VALUE;
               }

               long update = current - n;
               if (update < 0L) {
                  update = 0L;
               }

               if (!this.requested.compareAndSet(current, update)) {
                  continue;
               }

               return update;
            }

            return current;
         }
      }

      private long addCap(long n) {
         while (true) {
            long r = this.requested.get();
            if (!isTerminated(r) && !isCancelled(r) && r != Long.MAX_VALUE) {
               long u = addCap(r, n);
               if (!this.requested.compareAndSet(r, u)) {
                  continue;
               }

               return r;
            }

            return r;
         }
      }

      private static boolean isTerminated(long state) {
         return state == -1L;
      }

      private static boolean isCancelled(long state) {
         return state == Long.MIN_VALUE;
      }

      private static long addCap(long a, long b) {
         long res = a + b;
         return res < 0L ? Long.MAX_VALUE : res;
      }
   }
}
