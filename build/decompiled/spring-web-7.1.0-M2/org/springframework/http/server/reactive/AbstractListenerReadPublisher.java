package org.springframework.http.server.reactive;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.log.LogDelegateFactory;
import org.springframework.util.Assert;
import reactor.core.publisher.Operators;

public abstract class AbstractListenerReadPublisher<T> implements Publisher<T> {
   protected static Log rsReadLogger = LogDelegateFactory.getHiddenLog(AbstractListenerReadPublisher.class);
   static final DataBuffer EMPTY_BUFFER = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);
   private final AtomicReference<AbstractListenerReadPublisher.State> state = new AtomicReference<>(AbstractListenerReadPublisher.State.UNSUBSCRIBED);
   private volatile long demand;
   private static final AtomicLongFieldUpdater<AbstractListenerReadPublisher> DEMAND_FIELD_UPDATER = AtomicLongFieldUpdater.newUpdater(
      AbstractListenerReadPublisher.class, "demand"
   );
   private volatile @Nullable Subscriber<? super T> subscriber;
   private volatile boolean completionPending;
   private volatile @Nullable Throwable errorPending;
   private final String logPrefix;

   public AbstractListenerReadPublisher() {
      this("");
   }

   public AbstractListenerReadPublisher(String logPrefix) {
      this.logPrefix = logPrefix;
   }

   public String getLogPrefix() {
      return this.logPrefix;
   }

   public void subscribe(Subscriber<? super T> subscriber) {
      this.state.get().subscribe(this, subscriber);
   }

   public final void onDataAvailable() {
      rsReadLogger.trace(this.getLogPrefix() + "onDataAvailable");
      this.state.get().onDataAvailable(this);
   }

   public void onAllDataRead() {
      AbstractListenerReadPublisher.State state = this.state.get();
      if (rsReadLogger.isTraceEnabled()) {
         rsReadLogger.trace(this.getLogPrefix() + "onAllDataRead [" + state + "]");
      }

      state.onAllDataRead(this);
   }

   public final void onError(Throwable ex) {
      AbstractListenerReadPublisher.State state = this.state.get();
      if (rsReadLogger.isTraceEnabled()) {
         rsReadLogger.trace(this.getLogPrefix() + "onError: " + ex + " [" + state + "]");
      }

      state.onError(this, ex);
   }

   protected abstract void checkOnDataAvailable();

   protected abstract @Nullable T read() throws IOException;

   protected abstract void readingPaused();

   protected abstract void discardData();

   private boolean readAndPublish() throws IOException {
      while (true) {
         long r = this.demand;
         if (this.demand > 0L && this.state.get() != AbstractListenerReadPublisher.State.COMPLETED) {
            T data = this.read();
            if (data == EMPTY_BUFFER) {
               if (rsReadLogger.isTraceEnabled()) {
                  rsReadLogger.trace(this.getLogPrefix() + "0 bytes read, trying again");
               }
               continue;
            }

            if (data != null) {
               if (r != Long.MAX_VALUE) {
                  DEMAND_FIELD_UPDATER.addAndGet(this, -1L);
               }

               Subscriber<? super T> subscriber = this.subscriber;
               Assert.state(subscriber != null, "No subscriber");
               if (rsReadLogger.isTraceEnabled()) {
                  rsReadLogger.trace(this.getLogPrefix() + "Publishing " + data.getClass().getSimpleName());
               }

               subscriber.onNext(data);
               continue;
            }

            if (rsReadLogger.isTraceEnabled()) {
               rsReadLogger.trace(this.getLogPrefix() + "No more to read");
            }

            return true;
         }

         return false;
      }
   }

   private boolean changeState(AbstractListenerReadPublisher.State oldState, AbstractListenerReadPublisher.State newState) {
      boolean result = this.state.compareAndSet(oldState, newState);
      if (result && rsReadLogger.isTraceEnabled()) {
         rsReadLogger.trace(this.getLogPrefix() + oldState + " -> " + newState);
      }

      return result;
   }

   private void changeToDemandState(AbstractListenerReadPublisher.State oldState) {
      if (this.changeState(oldState, AbstractListenerReadPublisher.State.DEMAND) && oldState != AbstractListenerReadPublisher.State.READING) {
         this.checkOnDataAvailable();
      }
   }

   private boolean handlePendingCompletionOrError() {
      AbstractListenerReadPublisher.State state = this.state.get();
      if (state == AbstractListenerReadPublisher.State.DEMAND || state == AbstractListenerReadPublisher.State.NO_DEMAND) {
         if (this.completionPending) {
            rsReadLogger.trace(this.getLogPrefix() + "Processing pending completion");
            this.state.get().onAllDataRead(this);
            return true;
         }

         Throwable ex = this.errorPending;
         if (ex != null) {
            if (rsReadLogger.isTraceEnabled()) {
               rsReadLogger.trace(this.getLogPrefix() + "Processing pending completion with error: " + ex);
            }

            this.state.get().onError(this, ex);
            return true;
         }
      }

      return false;
   }

   private Subscription createSubscription() {
      return new AbstractListenerReadPublisher.ReadSubscription();
   }

   private final class ReadSubscription implements Subscription {
      public void request(long n) {
         if (AbstractListenerReadPublisher.rsReadLogger.isTraceEnabled()) {
            AbstractListenerReadPublisher.rsReadLogger
               .trace(AbstractListenerReadPublisher.this.getLogPrefix() + "request " + (n != Long.MAX_VALUE ? n : "Long.MAX_VALUE"));
         }

         AbstractListenerReadPublisher.this.state.get().request(AbstractListenerReadPublisher.this, n);
      }

      public void cancel() {
         AbstractListenerReadPublisher.State state = AbstractListenerReadPublisher.this.state.get();
         if (AbstractListenerReadPublisher.rsReadLogger.isTraceEnabled()) {
            AbstractListenerReadPublisher.rsReadLogger.trace(AbstractListenerReadPublisher.this.getLogPrefix() + "cancel [" + state + "]");
         }

         state.cancel(AbstractListenerReadPublisher.this);
      }
   }

   private enum State {
      UNSUBSCRIBED {
         @Override
         <T> void subscribe(AbstractListenerReadPublisher<T> publisher, Subscriber<? super T> subscriber) {
            Assert.notNull(publisher, "Publisher must not be null");
            Assert.notNull(subscriber, "Subscriber must not be null");
            if (publisher.changeState(this, SUBSCRIBING)) {
               Subscription subscription = publisher.createSubscription();
               publisher.subscriber = subscriber;
               subscriber.onSubscribe(subscription);
               publisher.changeState(SUBSCRIBING, NO_DEMAND);
               publisher.handlePendingCompletionOrError();
            } else {
               throw new IllegalStateException("Failed to transition to SUBSCRIBING, subscriber: " + subscriber);
            }
         }

         @Override
         <T> void onAllDataRead(AbstractListenerReadPublisher<T> publisher) {
            publisher.completionPending = true;
            publisher.handlePendingCompletionOrError();
         }

         @Override
         <T> void onError(AbstractListenerReadPublisher<T> publisher, Throwable ex) {
            publisher.errorPending = ex;
            publisher.handlePendingCompletionOrError();
         }
      },
      SUBSCRIBING {
         @Override
         <T> void request(AbstractListenerReadPublisher<T> publisher, long n) {
            if (Operators.validate(n)) {
               Operators.addCap(AbstractListenerReadPublisher.DEMAND_FIELD_UPDATER, publisher, n);
               publisher.changeToDemandState(this);
            }
         }

         @Override
         <T> void onAllDataRead(AbstractListenerReadPublisher<T> publisher) {
            publisher.completionPending = true;
            publisher.handlePendingCompletionOrError();
         }

         @Override
         <T> void onError(AbstractListenerReadPublisher<T> publisher, Throwable ex) {
            publisher.errorPending = ex;
            publisher.handlePendingCompletionOrError();
         }

         @Override
         <T> void cancel(AbstractListenerReadPublisher<T> publisher) {
            publisher.completionPending = true;
            publisher.handlePendingCompletionOrError();
         }
      },
      NO_DEMAND {
         @Override
         <T> void request(AbstractListenerReadPublisher<T> publisher, long n) {
            if (Operators.validate(n)) {
               Operators.addCap(AbstractListenerReadPublisher.DEMAND_FIELD_UPDATER, publisher, n);
               publisher.changeToDemandState(this);
            }
         }
      },
      DEMAND {
         @Override
         <T> void request(AbstractListenerReadPublisher<T> publisher, long n) {
            if (Operators.validate(n)) {
               Operators.addCap(AbstractListenerReadPublisher.DEMAND_FIELD_UPDATER, publisher, n);
               publisher.changeToDemandState(NO_DEMAND);
            }
         }

         @Override
         <T> void onDataAvailable(AbstractListenerReadPublisher<T> publisher) {
            if (publisher.changeState(this, READING)) {
               try {
                  boolean demandAvailable = publisher.readAndPublish();
                  if (demandAvailable) {
                     publisher.changeToDemandState(READING);
                     publisher.handlePendingCompletionOrError();
                  } else {
                     publisher.readingPaused();
                     if (publisher.changeState(READING, NO_DEMAND) && !publisher.handlePendingCompletionOrError()) {
                        long r = publisher.demand;
                        if (r > 0L) {
                           publisher.changeToDemandState(NO_DEMAND);
                        }
                     }
                  }
               } catch (IOException ex) {
                  publisher.onError(ex);
               }
            }
         }
      },
      READING {
         @Override
         <T> void request(AbstractListenerReadPublisher<T> publisher, long n) {
            if (Operators.validate(n)) {
               Operators.addCap(AbstractListenerReadPublisher.DEMAND_FIELD_UPDATER, publisher, n);
               publisher.changeToDemandState(NO_DEMAND);
            }
         }

         @Override
         <T> void onAllDataRead(AbstractListenerReadPublisher<T> publisher) {
            publisher.completionPending = true;
            publisher.handlePendingCompletionOrError();
         }

         @Override
         <T> void onError(AbstractListenerReadPublisher<T> publisher, Throwable ex) {
            publisher.errorPending = ex;
            publisher.handlePendingCompletionOrError();
         }

         @Override
         <T> void cancel(AbstractListenerReadPublisher<T> publisher) {
            publisher.discardData();
            publisher.completionPending = true;
            publisher.handlePendingCompletionOrError();
         }
      },
      COMPLETED {
         @Override
         <T> void request(AbstractListenerReadPublisher<T> publisher, long n) {
         }

         @Override
         <T> void cancel(AbstractListenerReadPublisher<T> publisher) {
         }

         @Override
         <T> void onAllDataRead(AbstractListenerReadPublisher<T> publisher) {
         }

         @Override
         <T> void onError(AbstractListenerReadPublisher<T> publisher, Throwable t) {
         }
      };

      <T> void subscribe(AbstractListenerReadPublisher<T> publisher, Subscriber<? super T> subscriber) {
         throw new IllegalStateException(this.toString());
      }

      <T> void request(AbstractListenerReadPublisher<T> publisher, long n) {
         throw new IllegalStateException(this.toString());
      }

      <T> void cancel(AbstractListenerReadPublisher<T> publisher) {
         if (publisher.changeState(this, COMPLETED)) {
            publisher.discardData();
         } else {
            publisher.state.get().cancel(publisher);
         }
      }

      <T> void onDataAvailable(AbstractListenerReadPublisher<T> publisher) {
      }

      <T> void onAllDataRead(AbstractListenerReadPublisher<T> publisher) {
         if (publisher.changeState(this, COMPLETED)) {
            Subscriber<? super T> s = publisher.subscriber;
            if (s != null) {
               s.onComplete();
            }
         } else {
            publisher.state.get().onAllDataRead(publisher);
         }
      }

      <T> void onError(AbstractListenerReadPublisher<T> publisher, Throwable t) {
         if (publisher.changeState(this, COMPLETED)) {
            publisher.discardData();
            Subscriber<? super T> s = publisher.subscriber;
            if (s != null) {
               s.onError(t);
            }
         } else {
            publisher.state.get().onError(publisher, t);
         }
      }
   }
}
