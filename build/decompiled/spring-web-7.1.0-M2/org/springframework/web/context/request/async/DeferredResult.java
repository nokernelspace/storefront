package org.springframework.web.context.request.async;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;

public class DeferredResult<T> {
   private static final Object RESULT_NONE = new Object();
   private static final Log logger = LogFactory.getLog(DeferredResult.class);
   private final @Nullable Long timeoutValue;
   private final Supplier<?> timeoutResult;
   private @Nullable Runnable timeoutCallback;
   private @Nullable Consumer<Throwable> errorCallback;
   private @Nullable Runnable completionCallback;
   private DeferredResult.@Nullable DeferredResultHandler resultHandler;
   private volatile @Nullable Object result = RESULT_NONE;
   private volatile boolean expired;

   public DeferredResult() {
      this(null);
   }

   public DeferredResult(@Nullable Long timeoutValue) {
      this(timeoutValue, () -> RESULT_NONE);
   }

   public DeferredResult(@Nullable Long timeoutValue, Object timeoutResult) {
      this(timeoutValue, () -> timeoutResult);
   }

   public DeferredResult(@Nullable Long timeoutValue, Supplier<?> timeoutResult) {
      this.timeoutValue = timeoutValue;
      this.timeoutResult = timeoutResult;
   }

   public final boolean isSetOrExpired() {
      return this.result != RESULT_NONE || this.expired;
   }

   public boolean hasResult() {
      return this.result != RESULT_NONE;
   }

   public @Nullable Object getResult() {
      Object resultToCheck = this.result;
      return resultToCheck != RESULT_NONE ? resultToCheck : null;
   }

   final @Nullable Long getTimeoutValue() {
      return this.timeoutValue;
   }

   public void onTimeout(Runnable callback) {
      this.timeoutCallback = callback;
   }

   public void onError(Consumer<Throwable> callback) {
      this.errorCallback = callback;
   }

   public void onCompletion(Runnable callback) {
      this.completionCallback = callback;
   }

   public final void setResultHandler(DeferredResult.DeferredResultHandler resultHandler) {
      Assert.notNull(resultHandler, "DeferredResultHandler is required");
      if (!this.expired) {
         Object resultToHandle;
         synchronized (this) {
            if (this.expired) {
               return;
            }

            resultToHandle = this.result;
            if (resultToHandle == RESULT_NONE) {
               this.resultHandler = resultHandler;
               return;
            }
         }

         try {
            resultHandler.handleResult(resultToHandle);
         } catch (Throwable ex) {
            logger.debug("Failed to process async result", ex);
         }
      }
   }

   public boolean setResult(@Nullable T result) {
      return this.setResultInternal(result);
   }

   private boolean setResultInternal(@Nullable Object result) {
      if (this.isSetOrExpired()) {
         return false;
      }

      DeferredResult.DeferredResultHandler resultHandlerToUse;
      synchronized (this) {
         if (this.isSetOrExpired()) {
            return false;
         }

         this.result = result;
         resultHandlerToUse = this.resultHandler;
         if (resultHandlerToUse == null) {
            return true;
         }

         this.resultHandler = null;
      }

      resultHandlerToUse.handleResult(result);
      return true;
   }

   public boolean setErrorResult(Object result) {
      return this.setResultInternal(result);
   }

   final DeferredResultProcessingInterceptor getLifecycleInterceptor() {
      return new DeferredResult.LifecycleInterceptor();
   }

   @FunctionalInterface
   public interface DeferredResultHandler {
      void handleResult(@Nullable Object result);
   }

   private class LifecycleInterceptor implements DeferredResultProcessingInterceptor {
      @Override
      public <S> boolean handleTimeout(NativeWebRequest request, DeferredResult<S> result) {
         boolean continueProcessing = true;

         try {
            if (DeferredResult.this.timeoutCallback != null) {
               DeferredResult.this.timeoutCallback.run();
            }
         } finally {
            Object value = DeferredResult.this.timeoutResult.get();
            if (value != DeferredResult.RESULT_NONE) {
               continueProcessing = false;

               try {
                  DeferredResult.this.setResultInternal(value);
               } catch (Throwable ex) {
                  DeferredResult.logger.debug("Failed to handle timeout result", ex);
               }
            }
         }

         return continueProcessing;
      }

      @Override
      public <S> boolean handleError(NativeWebRequest request, DeferredResult<S> result, Throwable t) {
         try {
            if (DeferredResult.this.errorCallback != null) {
               DeferredResult.this.errorCallback.accept(t);
            }
         } finally {
            try {
               DeferredResult.this.setResultInternal(t);
            } catch (Throwable ex) {
               DeferredResult.logger.debug("Failed to handle error result", ex);
            }
         }

         return false;
      }

      @Override
      public <S> void afterCompletion(NativeWebRequest request, DeferredResult<S> result) {
         DeferredResult.this.expired = true;
         if (DeferredResult.this.completionCallback != null) {
            DeferredResult.this.completionCallback.run();
         }
      }
   }
}
