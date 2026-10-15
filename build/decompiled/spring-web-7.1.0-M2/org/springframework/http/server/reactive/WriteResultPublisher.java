package org.springframework.http.server.reactive;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.log.LogDelegateFactory;
import org.springframework.util.Assert;
import reactor.core.publisher.Operators;

class WriteResultPublisher implements Publisher<Void> {
   private static final Log rsWriteResultLogger = LogDelegateFactory.getHiddenLog(WriteResultPublisher.class);
   private final AtomicReference<WriteResultPublisher.State> state = new AtomicReference<>(WriteResultPublisher.State.UNSUBSCRIBED);
   private final Runnable cancelTask;
   private volatile @Nullable Subscriber<? super Void> subscriber;
   private volatile boolean completedBeforeSubscribed;
   private volatile @Nullable Throwable errorBeforeSubscribed;
   private final String logPrefix;

   public WriteResultPublisher(String logPrefix, Runnable cancelTask) {
      this.cancelTask = cancelTask;
      this.logPrefix = logPrefix;
   }

   public final void subscribe(Subscriber<? super Void> subscriber) {
      if (rsWriteResultLogger.isTraceEnabled()) {
         rsWriteResultLogger.trace(this.logPrefix + "got subscriber " + subscriber);
      }

      this.state.get().subscribe(this, subscriber);
   }

   public void publishComplete() {
      WriteResultPublisher.State state = this.state.get();
      if (rsWriteResultLogger.isTraceEnabled()) {
         rsWriteResultLogger.trace(this.logPrefix + "completed [" + state + "]");
      }

      state.publishComplete(this);
   }

   public void publishError(Throwable t) {
      WriteResultPublisher.State state = this.state.get();
      if (rsWriteResultLogger.isTraceEnabled()) {
         rsWriteResultLogger.trace(this.logPrefix + "failed: " + t + " [" + state + "]");
      }

      state.publishError(this, t);
   }

   private boolean changeState(WriteResultPublisher.State oldState, WriteResultPublisher.State newState) {
      return this.state.compareAndSet(oldState, newState);
   }

   private enum State {
      UNSUBSCRIBED {
         @Override
         void subscribe(WriteResultPublisher publisher, Subscriber<? super Void> subscriber) {
            Assert.notNull(subscriber, "Subscriber must not be null");
            if (publisher.changeState(this, SUBSCRIBING)) {
               Subscription subscription = new WriteResultPublisher.WriteResultSubscription(publisher);
               publisher.subscriber = subscriber;
               subscriber.onSubscribe(subscription);
               publisher.changeState(SUBSCRIBING, SUBSCRIBED);
               if (publisher.completedBeforeSubscribed) {
                  publisher.state.get().publishComplete(publisher);
               }

               Throwable ex = publisher.errorBeforeSubscribed;
               if (ex != null) {
                  publisher.state.get().publishError(publisher, ex);
               }
            } else {
               throw new IllegalStateException(this.toString());
            }
         }

         @Override
         void publishComplete(WriteResultPublisher publisher) {
            publisher.completedBeforeSubscribed = true;
            if (WriteResultPublisher.State.SUBSCRIBED == publisher.state.get()) {
               publisher.state.get().publishComplete(publisher);
            }
         }

         @Override
         void publishError(WriteResultPublisher publisher, Throwable ex) {
            publisher.errorBeforeSubscribed = ex;
            if (WriteResultPublisher.State.SUBSCRIBED == publisher.state.get()) {
               publisher.state.get().publishError(publisher, ex);
            }
         }
      },
      SUBSCRIBING {
         @Override
         void request(WriteResultPublisher publisher, long n) {
            Operators.validate(n);
         }

         @Override
         void publishComplete(WriteResultPublisher publisher) {
            publisher.completedBeforeSubscribed = true;
            if (WriteResultPublisher.State.SUBSCRIBED == publisher.state.get()) {
               publisher.state.get().publishComplete(publisher);
            }
         }

         @Override
         void publishError(WriteResultPublisher publisher, Throwable ex) {
            publisher.errorBeforeSubscribed = ex;
            if (WriteResultPublisher.State.SUBSCRIBED == publisher.state.get()) {
               publisher.state.get().publishError(publisher, ex);
            }
         }
      },
      SUBSCRIBED {
         @Override
         void request(WriteResultPublisher publisher, long n) {
            Operators.validate(n);
         }
      },
      COMPLETED {
         @Override
         void request(WriteResultPublisher publisher, long n) {
         }

         @Override
         void cancel(WriteResultPublisher publisher) {
         }

         @Override
         void publishComplete(WriteResultPublisher publisher) {
         }

         @Override
         void publishError(WriteResultPublisher publisher, Throwable t) {
         }
      };

      void subscribe(WriteResultPublisher publisher, Subscriber<? super Void> subscriber) {
         throw new IllegalStateException(this.toString());
      }

      void request(WriteResultPublisher publisher, long n) {
         throw new IllegalStateException(this.toString());
      }

      void cancel(WriteResultPublisher publisher) {
         if (publisher.changeState(this, COMPLETED)) {
            publisher.cancelTask.run();
         } else {
            publisher.state.get().cancel(publisher);
         }
      }

      void publishComplete(WriteResultPublisher publisher) {
         if (publisher.changeState(this, COMPLETED)) {
            Subscriber<? super Void> s = publisher.subscriber;
            Assert.state(s != null, "No subscriber");
            s.onComplete();
         } else {
            publisher.state.get().publishComplete(publisher);
         }
      }

      void publishError(WriteResultPublisher publisher, Throwable t) {
         if (publisher.changeState(this, COMPLETED)) {
            Subscriber<? super Void> s = publisher.subscriber;
            Assert.state(s != null, "No subscriber");
            s.onError(t);
         } else {
            publisher.state.get().publishError(publisher, t);
         }
      }
   }

   private static final class WriteResultSubscription implements Subscription {
      private final WriteResultPublisher publisher;

      public WriteResultSubscription(WriteResultPublisher publisher) {
         this.publisher = publisher;
      }

      public final void request(long n) {
         if (WriteResultPublisher.rsWriteResultLogger.isTraceEnabled()) {
            WriteResultPublisher.rsWriteResultLogger.trace(this.publisher.logPrefix + "request " + (n != Long.MAX_VALUE ? n : "Long.MAX_VALUE"));
         }

         this.getState().request(this.publisher, n);
      }

      public final void cancel() {
         WriteResultPublisher.State state = this.getState();
         if (WriteResultPublisher.rsWriteResultLogger.isTraceEnabled()) {
            WriteResultPublisher.rsWriteResultLogger.trace(this.publisher.logPrefix + "cancel [" + state + "]");
         }

         state.cancel(this.publisher);
      }

      private WriteResultPublisher.State getState() {
         return this.publisher.state.get();
      }
   }
}
