package org.springframework.http.server.reactive;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.log.LogDelegateFactory;
import org.springframework.util.Assert;

public abstract class AbstractListenerWriteFlushProcessor<T> implements Processor<Publisher<? extends T>, Void> {
   protected static final Log rsWriteFlushLogger = LogDelegateFactory.getHiddenLog(AbstractListenerWriteFlushProcessor.class);
   private final AtomicReference<AbstractListenerWriteFlushProcessor.State> state = new AtomicReference<>(
      AbstractListenerWriteFlushProcessor.State.UNSUBSCRIBED
   );
   private @Nullable Subscription subscription;
   private volatile boolean sourceCompleted;
   private volatile @Nullable AbstractListenerWriteProcessor<?> currentWriteProcessor;
   private final WriteResultPublisher resultPublisher;
   private final String logPrefix;

   public AbstractListenerWriteFlushProcessor() {
      this("");
   }

   public AbstractListenerWriteFlushProcessor(String logPrefix) {
      this.logPrefix = logPrefix;
      this.resultPublisher = new WriteResultPublisher(logPrefix + "[WFP] ", () -> {
         this.cancel();
         AbstractListenerWriteFlushProcessor.State oldState = this.state.getAndSet(AbstractListenerWriteFlushProcessor.State.COMPLETED);
         if (rsWriteFlushLogger.isTraceEnabled()) {
            rsWriteFlushLogger.trace(this.getLogPrefix() + oldState + " -> " + this.state);
         }

         AbstractListenerWriteProcessor<?> writeProcessor = this.currentWriteProcessor;
         if (writeProcessor != null) {
            writeProcessor.cancelAndSetCompleted();
         }

         this.currentWriteProcessor = null;
      });
   }

   public String getLogPrefix() {
      return this.logPrefix;
   }

   public final void onSubscribe(Subscription subscription) {
      this.state.get().onSubscribe(this, subscription);
   }

   public final void onNext(Publisher<? extends T> publisher) {
      if (rsWriteFlushLogger.isTraceEnabled()) {
         rsWriteFlushLogger.trace(this.getLogPrefix() + "onNext: \"write\" Publisher");
      }

      this.state.get().onNext(this, publisher);
   }

   public final void onError(Throwable ex) {
      AbstractListenerWriteFlushProcessor.State state = this.state.get();
      if (rsWriteFlushLogger.isTraceEnabled()) {
         rsWriteFlushLogger.trace(this.getLogPrefix() + "onError: " + ex + " [" + state + "]");
      }

      state.onError(this, ex);
   }

   public final void onComplete() {
      AbstractListenerWriteFlushProcessor.State state = this.state.get();
      if (rsWriteFlushLogger.isTraceEnabled()) {
         rsWriteFlushLogger.trace(this.getLogPrefix() + "onComplete [" + state + "]");
      }

      state.onComplete(this);
   }

   protected final void onFlushPossible() {
      this.state.get().onFlushPossible(this);
   }

   protected void cancel() {
      if (rsWriteFlushLogger.isTraceEnabled()) {
         rsWriteFlushLogger.trace(this.getLogPrefix() + "cancel [" + this.state + "]");
      }

      if (this.subscription != null) {
         this.subscription.cancel();
      }
   }

   public final void subscribe(Subscriber<? super Void> subscriber) {
      this.resultPublisher.subscribe(subscriber);
   }

   protected abstract Processor<? super T, Void> createWriteProcessor();

   protected abstract boolean isWritePossible();

   protected abstract void flush() throws IOException;

   protected abstract boolean isFlushPending();

   protected void flushingFailed(Throwable t) {
      this.cancel();
      this.onError(t);
   }

   private boolean changeState(AbstractListenerWriteFlushProcessor.State oldState, AbstractListenerWriteFlushProcessor.State newState) {
      boolean result = this.state.compareAndSet(oldState, newState);
      if (result && rsWriteFlushLogger.isTraceEnabled()) {
         rsWriteFlushLogger.trace(this.getLogPrefix() + oldState + " -> " + newState);
      }

      return result;
   }

   private void flushIfPossible() {
      boolean result = this.isWritePossible();
      if (rsWriteFlushLogger.isTraceEnabled()) {
         rsWriteFlushLogger.trace(this.getLogPrefix() + "isWritePossible[" + result + "]");
      }

      if (result) {
         this.onFlushPossible();
      }
   }

   private enum State {
      UNSUBSCRIBED {
         @Override
         public <T> void onSubscribe(AbstractListenerWriteFlushProcessor<T> processor, Subscription subscription) {
            Assert.notNull(subscription, "Subscription must not be null");
            if (processor.changeState(this, REQUESTED)) {
               processor.subscription = subscription;
               subscription.request(1L);
            } else {
               super.onSubscribe(processor, subscription);
            }
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteFlushProcessor<T> processor) {
            if (processor.changeState(this, COMPLETED)) {
               processor.resultPublisher.publishComplete();
            } else {
               processor.state.get().onComplete(processor);
            }
         }
      },
      REQUESTED {
         @Override
         public <T> void onNext(AbstractListenerWriteFlushProcessor<T> processor, Publisher<? extends T> currentPublisher) {
            if (processor.changeState(this, RECEIVED)) {
               Processor<? super T, Void> writeProcessor = processor.createWriteProcessor();
               processor.currentWriteProcessor = (AbstractListenerWriteProcessor<?>)writeProcessor;
               currentPublisher.subscribe(writeProcessor);
               writeProcessor.subscribe(new AbstractListenerWriteFlushProcessor.State.WriteResultSubscriber(processor));
            }
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteFlushProcessor<T> processor) {
            if (processor.changeState(this, COMPLETED)) {
               processor.resultPublisher.publishComplete();
            } else {
               processor.state.get().onComplete(processor);
            }
         }
      },
      RECEIVED {
         @Override
         public <T> void writeComplete(AbstractListenerWriteFlushProcessor<T> processor) {
            try {
               processor.flush();
            } catch (Throwable ex) {
               processor.flushingFailed(ex);
               return;
            }

            if (processor.changeState(this, REQUESTED)) {
               if (processor.sourceCompleted) {
                  this.handleSourceCompleted(processor);
               } else {
                  Assert.state(processor.subscription != null, "No subscription");
                  processor.subscription.request(1L);
               }
            }
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteFlushProcessor<T> processor) {
            processor.sourceCompleted = true;
            if (processor.state.get() == AbstractListenerWriteFlushProcessor.State.REQUESTED) {
               this.handleSourceCompleted(processor);
            }
         }

         private <T> void handleSourceCompleted(AbstractListenerWriteFlushProcessor<T> processor) {
            if (processor.isFlushPending()) {
               processor.changeState(AbstractListenerWriteFlushProcessor.State.REQUESTED, AbstractListenerWriteFlushProcessor.State.FLUSHING);
               processor.flushIfPossible();
            } else if (processor.changeState(AbstractListenerWriteFlushProcessor.State.REQUESTED, AbstractListenerWriteFlushProcessor.State.COMPLETED)) {
               processor.resultPublisher.publishComplete();
            } else {
               processor.state.get().onComplete(processor);
            }
         }
      },
      FLUSHING {
         @Override
         public <T> void onFlushPossible(AbstractListenerWriteFlushProcessor<T> processor) {
            try {
               processor.flush();
            } catch (Throwable ex) {
               processor.flushingFailed(ex);
               return;
            }

            if (processor.changeState(this, COMPLETED)) {
               processor.resultPublisher.publishComplete();
            } else {
               processor.state.get().onComplete(processor);
            }
         }

         @Override
         public <T> void onNext(AbstractListenerWriteFlushProcessor<T> proc, Publisher<? extends T> pub) {
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteFlushProcessor<T> processor) {
         }
      },
      COMPLETED {
         @Override
         public <T> void onNext(AbstractListenerWriteFlushProcessor<T> proc, Publisher<? extends T> pub) {
         }

         @Override
         public <T> void onError(AbstractListenerWriteFlushProcessor<T> processor, Throwable t) {
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteFlushProcessor<T> processor) {
         }
      };

      public <T> void onSubscribe(AbstractListenerWriteFlushProcessor<T> proc, Subscription subscription) {
         subscription.cancel();
      }

      public <T> void onNext(AbstractListenerWriteFlushProcessor<T> proc, Publisher<? extends T> pub) {
         throw new IllegalStateException(this.toString());
      }

      public <T> void onError(AbstractListenerWriteFlushProcessor<T> processor, Throwable ex) {
         if (processor.changeState(this, COMPLETED)) {
            processor.resultPublisher.publishError(ex);
         } else {
            processor.state.get().onError(processor, ex);
         }
      }

      public <T> void onComplete(AbstractListenerWriteFlushProcessor<T> processor) {
         throw new IllegalStateException(this.toString());
      }

      public <T> void writeComplete(AbstractListenerWriteFlushProcessor<T> processor) {
         throw new IllegalStateException(this.toString());
      }

      public <T> void onFlushPossible(AbstractListenerWriteFlushProcessor<T> processor) {
      }

      private static class WriteResultSubscriber implements Subscriber<Void> {
         private final AbstractListenerWriteFlushProcessor<?> processor;

         public WriteResultSubscriber(AbstractListenerWriteFlushProcessor<?> processor) {
            this.processor = processor;
         }

         public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
         }

         public void onNext(Void aVoid) {
         }

         public void onError(Throwable ex) {
            if (AbstractListenerWriteFlushProcessor.rsWriteFlushLogger.isTraceEnabled()) {
               AbstractListenerWriteFlushProcessor.rsWriteFlushLogger.trace(this.processor.getLogPrefix() + "current \"write\" Publisher failed: " + ex);
            }

            this.processor.currentWriteProcessor = null;
            this.processor.cancel();
            this.processor.onError(ex);
         }

         public void onComplete() {
            if (AbstractListenerWriteFlushProcessor.rsWriteFlushLogger.isTraceEnabled()) {
               AbstractListenerWriteFlushProcessor.rsWriteFlushLogger.trace(this.processor.getLogPrefix() + "current \"write\" Publisher completed");
            }

            this.processor.currentWriteProcessor = null;
            this.processor.state.get().writeComplete(this.processor);
         }

         @Override
         public String toString() {
            return this.processor.getClass().getSimpleName() + "-WriteResultSubscriber";
         }
      }
   }
}
