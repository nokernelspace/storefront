package org.springframework.http.server.reactive;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.Assert;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.Scannable.Attr;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

public class ChannelSendOperator<T> extends Mono<Void> implements Scannable {
   private final Function<Publisher<T>, Publisher<Void>> writeFunction;
   private final Flux<T> source;

   public ChannelSendOperator(Publisher<? extends T> source, Function<Publisher<T>, Publisher<Void>> writeFunction) {
      this.source = Flux.from(source);
      this.writeFunction = writeFunction;
   }

   public @Nullable Object scanUnsafe(Attr key) {
      if (key == Attr.PREFETCH) {
         return Integer.MAX_VALUE;
      } else {
         return key == Attr.PARENT ? this.source : null;
      }
   }

   public void subscribe(CoreSubscriber<? super Void> actual) {
      this.source.subscribe(new ChannelSendOperator.WriteBarrier(actual));
   }

   private enum State {
      NEW,
      FIRST_SIGNAL_RECEIVED,
      EMITTING_CACHED_SIGNALS,
      READY_TO_WRITE;
   }

   private class WriteBarrier implements CoreSubscriber<T>, Subscription, Publisher<T> {
      private final ChannelSendOperator<T>.WriteCompletionBarrier writeCompletionBarrier;
      private @Nullable Subscription subscription;
      private @Nullable Object item;
      private @Nullable Throwable error;
      private boolean completed = false;
      private long demandBeforeReadyToWrite;
      private ChannelSendOperator.State state = ChannelSendOperator.State.NEW;
      private @Nullable Subscriber<? super T> writeSubscriber;

      WriteBarrier(CoreSubscriber<? super Void> completionSubscriber) {
         this.writeCompletionBarrier = ChannelSendOperator.this.new WriteCompletionBarrier(completionSubscriber, this);
      }

      public final void onSubscribe(Subscription s) {
         if (Operators.validate(this.subscription, s)) {
            this.subscription = s;
            this.writeCompletionBarrier.connect();
            s.request(1L);
         }
      }

      public final void onNext(T item) {
         if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
            this.requiredWriteSubscriber().onNext(item);
         } else {
            synchronized (this) {
               if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
                  this.requiredWriteSubscriber().onNext(item);
               } else if (this.state == ChannelSendOperator.State.NEW) {
                  this.item = item;
                  this.state = ChannelSendOperator.State.FIRST_SIGNAL_RECEIVED;

                  Publisher<Void> result;
                  try {
                     result = ChannelSendOperator.this.writeFunction.apply(this);
                  } catch (Throwable ex) {
                     this.writeCompletionBarrier.onError(ex);
                     return;
                  }

                  result.subscribe(this.writeCompletionBarrier);
               } else {
                  if (this.subscription != null) {
                     this.subscription.cancel();
                  }

                  this.writeCompletionBarrier.onError(new IllegalStateException("Unexpected item."));
               }
            }
         }
      }

      private Subscriber<? super T> requiredWriteSubscriber() {
         Assert.state(this.writeSubscriber != null, "No write subscriber");
         return this.writeSubscriber;
      }

      public final void onError(Throwable ex) {
         if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
            this.requiredWriteSubscriber().onError(ex);
         } else {
            synchronized (this) {
               if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
                  this.requiredWriteSubscriber().onError(ex);
               } else if (this.state == ChannelSendOperator.State.NEW) {
                  this.state = ChannelSendOperator.State.FIRST_SIGNAL_RECEIVED;
                  this.writeCompletionBarrier.onError(ex);
               } else {
                  this.error = ex;
               }
            }
         }
      }

      public final void onComplete() {
         if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
            this.requiredWriteSubscriber().onComplete();
         } else {
            synchronized (this) {
               if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
                  this.requiredWriteSubscriber().onComplete();
               } else if (this.state == ChannelSendOperator.State.NEW) {
                  this.completed = true;
                  this.state = ChannelSendOperator.State.FIRST_SIGNAL_RECEIVED;

                  Publisher<Void> result;
                  try {
                     result = ChannelSendOperator.this.writeFunction.apply(this);
                  } catch (Throwable ex) {
                     this.writeCompletionBarrier.onError(ex);
                     return;
                  }

                  result.subscribe(this.writeCompletionBarrier);
               } else {
                  this.completed = true;
               }
            }
         }
      }

      public Context currentContext() {
         return this.writeCompletionBarrier.currentContext();
      }

      public void request(long n) {
         Subscription s = this.subscription;
         if (s != null) {
            if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
               s.request(n);
            } else {
               synchronized (this) {
                  if (this.state == ChannelSendOperator.State.READY_TO_WRITE) {
                     s.request(n);
                     return;
                  }

                  label77:
                  if (this.writeSubscriber != null) {
                     if (this.state == ChannelSendOperator.State.EMITTING_CACHED_SIGNALS) {
                        this.demandBeforeReadyToWrite = n;
                        return;
                     }

                     try {
                        this.state = ChannelSendOperator.State.EMITTING_CACHED_SIGNALS;
                        if (this.emitCachedSignals()) {
                           return;
                        }

                        n = n + this.demandBeforeReadyToWrite - 1L;
                        if (n != 0L) {
                           break label77;
                        }
                     } finally {
                        this.state = ChannelSendOperator.State.READY_TO_WRITE;
                     }

                     return;
                  }
               }

               s.request(n);
            }
         }
      }

      private boolean emitCachedSignals() {
         Throwable error = this.error;
         if (error != null) {
            try {
               this.requiredWriteSubscriber().onError(error);
            } finally {
               this.releaseCachedItem();
            }

            return true;
         } else {
            T item = (T)this.item;
            this.item = null;
            if (item != null) {
               this.requiredWriteSubscriber().onNext(item);
            }

            if (this.completed) {
               this.requiredWriteSubscriber().onComplete();
               return true;
            } else {
               return false;
            }
         }
      }

      public void cancel() {
         Subscription s = this.subscription;
         if (s != null) {
            this.subscription = null;

            try {
               s.cancel();
            } finally {
               this.releaseCachedItem();
            }
         }
      }

      private void releaseCachedItem() {
         synchronized (this) {
            if (this.item instanceof DataBuffer dataBuffer) {
               DataBufferUtils.release(dataBuffer);
            }

            this.item = null;
         }
      }

      public void subscribe(Subscriber<? super T> writeSubscriber) {
         synchronized (this) {
            Assert.state(this.writeSubscriber == null, "Only one write subscriber supported");
            this.writeSubscriber = writeSubscriber;
            if (this.error == null && !this.completed) {
               this.writeSubscriber.onSubscribe(this);
            } else {
               this.writeSubscriber.onSubscribe(Operators.emptySubscription());
               this.emitCachedSignals();
            }
         }
      }
   }

   private class WriteCompletionBarrier implements CoreSubscriber<Void>, Subscription {
      private final CoreSubscriber<? super Void> completionSubscriber;
      private final ChannelSendOperator<T>.WriteBarrier writeBarrier;
      private @Nullable Subscription subscription;

      public WriteCompletionBarrier(CoreSubscriber<? super Void> subscriber, ChannelSendOperator<T>.WriteBarrier writeBarrier) {
         this.completionSubscriber = subscriber;
         this.writeBarrier = writeBarrier;
      }

      public void connect() {
         this.completionSubscriber.onSubscribe(this);
      }

      public void onSubscribe(Subscription subscription) {
         this.subscription = subscription;
         subscription.request(Long.MAX_VALUE);
      }

      public void onNext(Void aVoid) {
      }

      public void onError(Throwable ex) {
         try {
            this.completionSubscriber.onError(ex);
         } finally {
            this.writeBarrier.releaseCachedItem();
         }
      }

      public void onComplete() {
         this.completionSubscriber.onComplete();
      }

      public Context currentContext() {
         return this.completionSubscriber.currentContext();
      }

      public void request(long n) {
      }

      public void cancel() {
         this.writeBarrier.cancel();
         Subscription subscription = this.subscription;
         if (subscription != null) {
            subscription.cancel();
         }
      }
   }
}
