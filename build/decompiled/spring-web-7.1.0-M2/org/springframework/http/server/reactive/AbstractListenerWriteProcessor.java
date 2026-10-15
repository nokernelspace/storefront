package org.springframework.http.server.reactive;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.core.log.LogDelegateFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class AbstractListenerWriteProcessor<T> implements Processor<T, Void> {
   protected static final Log rsWriteLogger = LogDelegateFactory.getHiddenLog(AbstractListenerWriteProcessor.class);
   private final AtomicReference<AbstractListenerWriteProcessor.State> state = new AtomicReference<>(AbstractListenerWriteProcessor.State.UNSUBSCRIBED);
   private @Nullable Subscription subscription;
   private volatile @Nullable T currentData;
   private volatile boolean sourceCompleted;
   private volatile boolean readyToCompleteAfterLastWrite;
   private final WriteResultPublisher resultPublisher;
   private final String logPrefix;

   public AbstractListenerWriteProcessor() {
      this("");
   }

   public AbstractListenerWriteProcessor(String logPrefix) {
      this.resultPublisher = new WriteResultPublisher(logPrefix + "[WP] ", this::cancelAndSetCompleted);
      this.logPrefix = StringUtils.hasText(logPrefix) ? logPrefix : "";
   }

   public String getLogPrefix() {
      return this.logPrefix;
   }

   public final void onSubscribe(Subscription subscription) {
      this.state.get().onSubscribe(this, subscription);
   }

   public final void onNext(T data) {
      if (rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + "onNext: " + data.getClass().getSimpleName());
      }

      this.state.get().onNext(this, data);
   }

   public final void onError(Throwable ex) {
      AbstractListenerWriteProcessor.State state = this.state.get();
      if (rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + "onError: " + ex + " [" + state + "]");
      }

      state.onError(this, ex);
   }

   public final void onComplete() {
      AbstractListenerWriteProcessor.State state = this.state.get();
      if (rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + "onComplete [" + state + "]");
      }

      state.onComplete(this);
   }

   public final void onWritePossible() {
      AbstractListenerWriteProcessor.State state = this.state.get();
      if (rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + "onWritePossible [" + state + "]");
      }

      state.onWritePossible(this);
   }

   public void cancel() {
      if (rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + "cancel [" + this.state + "]");
      }

      if (this.subscription != null) {
         this.subscription.cancel();
      }
   }

   void cancelAndSetCompleted() {
      this.cancel();

      while (true) {
         AbstractListenerWriteProcessor.State prev = this.state.get();
         if (prev == AbstractListenerWriteProcessor.State.COMPLETED) {
            break;
         }

         if (this.state.compareAndSet(prev, AbstractListenerWriteProcessor.State.COMPLETED)) {
            if (rsWriteLogger.isTraceEnabled()) {
               rsWriteLogger.trace(this.getLogPrefix() + prev + " -> " + this.state);
            }

            if (prev != AbstractListenerWriteProcessor.State.WRITING) {
               this.discardCurrentData();
            }
            break;
         }
      }
   }

   public final void subscribe(Subscriber<? super Void> subscriber) {
      this.resultPublisher.subscribe(subscriber);
   }

   protected abstract boolean isDataEmpty(T data);

   protected void dataReceived(T data) {
      T prev = this.currentData;
      if (prev != null) {
         this.discardData(data);
         this.cancel();
         this.onError(new IllegalStateException("Received new data while current not processed yet."));
      }

      this.currentData = data;
   }

   protected abstract boolean isWritePossible();

   protected abstract boolean write(T data) throws IOException;

   protected void writingComplete() {
   }

   protected void writingFailed(Throwable ex) {
   }

   protected abstract void discardData(T data);

   private boolean changeState(AbstractListenerWriteProcessor.State oldState, AbstractListenerWriteProcessor.State newState) {
      boolean result = this.state.compareAndSet(oldState, newState);
      if (result && rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + oldState + " -> " + newState);
      }

      return result;
   }

   private void changeStateToReceived(AbstractListenerWriteProcessor.State oldState) {
      if (this.changeState(oldState, AbstractListenerWriteProcessor.State.RECEIVED)) {
         this.writeIfPossible();
      }
   }

   private void changeStateToComplete(AbstractListenerWriteProcessor.State oldState) {
      if (this.changeState(oldState, AbstractListenerWriteProcessor.State.COMPLETED)) {
         this.discardCurrentData();
         this.writingComplete();
         this.resultPublisher.publishComplete();
      } else {
         this.state.get().onComplete(this);
      }
   }

   private void writeIfPossible() {
      boolean result = this.isWritePossible();
      if (!result && rsWriteLogger.isTraceEnabled()) {
         rsWriteLogger.trace(this.getLogPrefix() + "isWritePossible false");
      }

      if (result) {
         this.onWritePossible();
      }
   }

   private void discardCurrentData() {
      T data = this.currentData;
      this.currentData = null;
      if (data != null) {
         this.discardData(data);
      }
   }

   private enum State {
      UNSUBSCRIBED {
         @Override
         public <T> void onSubscribe(AbstractListenerWriteProcessor<T> processor, Subscription subscription) {
            Assert.notNull(subscription, "Subscription must not be null");
            if (processor.changeState(this, REQUESTED)) {
               processor.subscription = subscription;
               subscription.request(1L);
            } else {
               super.onSubscribe(processor, subscription);
            }
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteProcessor<T> processor) {
            processor.changeStateToComplete(this);
         }
      },
      REQUESTED {
         @Override
         public <T> void onNext(AbstractListenerWriteProcessor<T> processor, T data) {
            if (processor.isDataEmpty(data)) {
               Assert.state(processor.subscription != null, "No subscription");
               processor.subscription.request(1L);
            } else {
               processor.dataReceived(data);
               processor.changeStateToReceived(this);
            }
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteProcessor<T> processor) {
            processor.readyToCompleteAfterLastWrite = true;
            processor.changeStateToReceived(this);
         }
      },
      RECEIVED {
         @Override
         public <T> void onWritePossible(AbstractListenerWriteProcessor<T> processor) {
            if (processor.readyToCompleteAfterLastWrite) {
               processor.changeStateToComplete(RECEIVED);
            } else if (processor.changeState(this, WRITING)) {
               T data = processor.currentData;
               Assert.state(data != null, "No data");

               try {
                  if (processor.write(data)) {
                     if (processor.changeState(WRITING, REQUESTED)) {
                        processor.currentData = null;
                        if (processor.sourceCompleted) {
                           processor.readyToCompleteAfterLastWrite = true;
                           processor.changeStateToReceived(REQUESTED);
                        } else {
                           Assert.state(processor.subscription != null, "No subscription");
                           processor.subscription.request(1L);
                        }
                     }
                  } else {
                     processor.changeStateToReceived(WRITING);
                  }
               } catch (IOException ex) {
                  processor.writingFailed(ex);
               }
            }
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteProcessor<T> processor) {
            processor.sourceCompleted = true;
            if (processor.state.get() == AbstractListenerWriteProcessor.State.REQUESTED) {
               processor.changeStateToComplete(AbstractListenerWriteProcessor.State.REQUESTED);
            }
         }
      },
      WRITING {
         @Override
         public <T> void onComplete(AbstractListenerWriteProcessor<T> processor) {
            processor.sourceCompleted = true;
            if (processor.state.get() == AbstractListenerWriteProcessor.State.REQUESTED) {
               processor.changeStateToComplete(AbstractListenerWriteProcessor.State.REQUESTED);
            }
         }
      },
      COMPLETED {
         @Override
         public <T> void onNext(AbstractListenerWriteProcessor<T> processor, T data) {
         }

         @Override
         public <T> void onError(AbstractListenerWriteProcessor<T> processor, Throwable ex) {
         }

         @Override
         public <T> void onComplete(AbstractListenerWriteProcessor<T> processor) {
         }
      };

      public <T> void onSubscribe(AbstractListenerWriteProcessor<T> processor, Subscription subscription) {
         subscription.cancel();
      }

      public <T> void onNext(AbstractListenerWriteProcessor<T> processor, T data) {
         processor.discardData(data);
         processor.cancel();
         processor.onError(new IllegalStateException("Illegal onNext without demand"));
      }

      public <T> void onError(AbstractListenerWriteProcessor<T> processor, Throwable ex) {
         if (processor.changeState(this, COMPLETED)) {
            processor.discardCurrentData();
            processor.writingComplete();
            processor.resultPublisher.publishError(ex);
         } else {
            processor.state.get().onError(processor, ex);
         }
      }

      public <T> void onComplete(AbstractListenerWriteProcessor<T> processor) {
         throw new IllegalStateException(this.toString());
      }

      public <T> void onWritePossible(AbstractListenerWriteProcessor<T> processor) {
      }
   }
}
