package org.springframework.web.context.request.async;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.Assert;

public final class WebAsyncManager {
   private static final Object RESULT_NONE = new Object();
   private static final AsyncTaskExecutor DEFAULT_TASK_EXECUTOR = new SimpleAsyncTaskExecutor(WebAsyncManager.class.getSimpleName());
   private static final Log logger = LogFactory.getLog(WebAsyncManager.class);
   private static final CallableProcessingInterceptor timeoutCallableInterceptor = new TimeoutCallableProcessingInterceptor();
   private static final DeferredResultProcessingInterceptor timeoutDeferredResultInterceptor = new TimeoutDeferredResultProcessingInterceptor();
   private @Nullable AsyncWebRequest asyncWebRequest;
   private AsyncTaskExecutor taskExecutor = DEFAULT_TASK_EXECUTOR;
   private boolean isMultipartRequestParsed;
   private volatile @Nullable Object concurrentResult = RESULT_NONE;
   private volatile Object @Nullable [] concurrentResultContext;
   private final AtomicReference<WebAsyncManager.State> state = new AtomicReference<>(WebAsyncManager.State.NOT_STARTED);
   private final Map<Object, CallableProcessingInterceptor> callableInterceptors = new LinkedHashMap<>();
   private final Map<Object, DeferredResultProcessingInterceptor> deferredResultInterceptors = new LinkedHashMap<>();

   WebAsyncManager() {
   }

   public void setAsyncWebRequest(AsyncWebRequest asyncWebRequest) {
      Assert.notNull(asyncWebRequest, "AsyncWebRequest must not be null");
      this.asyncWebRequest = asyncWebRequest;
      this.asyncWebRequest.addCompletionHandler(() -> asyncWebRequest.removeAttribute(WebAsyncUtils.WEB_ASYNC_MANAGER_ATTRIBUTE, 0));
   }

   public @Nullable AsyncWebRequest getAsyncWebRequest() {
      return this.asyncWebRequest;
   }

   public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
      this.taskExecutor = taskExecutor;
   }

   public boolean isConcurrentHandlingStarted() {
      return this.asyncWebRequest != null && this.asyncWebRequest.isAsyncStarted();
   }

   public boolean hasConcurrentResult() {
      return this.concurrentResult != RESULT_NONE;
   }

   public @Nullable Object getConcurrentResult() {
      return this.concurrentResult;
   }

   public Object @Nullable [] getConcurrentResultContext() {
      return this.concurrentResultContext;
   }

   public @Nullable CallableProcessingInterceptor getCallableInterceptor(Object key) {
      return this.callableInterceptors.get(key);
   }

   public @Nullable DeferredResultProcessingInterceptor getDeferredResultInterceptor(Object key) {
      return this.deferredResultInterceptors.get(key);
   }

   public void registerCallableInterceptor(Object key, CallableProcessingInterceptor interceptor) {
      Assert.notNull(key, "Key is required");
      Assert.notNull(interceptor, "CallableProcessingInterceptor is required");
      this.callableInterceptors.put(key, interceptor);
   }

   public void registerCallableInterceptors(CallableProcessingInterceptor... interceptors) {
      Assert.notNull(interceptors, "A CallableProcessingInterceptor is required");

      for (CallableProcessingInterceptor interceptor : interceptors) {
         String key = interceptor.getClass().getName() + ":" + interceptor.hashCode();
         this.callableInterceptors.put(key, interceptor);
      }
   }

   public void registerDeferredResultInterceptor(Object key, DeferredResultProcessingInterceptor interceptor) {
      Assert.notNull(key, "Key is required");
      Assert.notNull(interceptor, "DeferredResultProcessingInterceptor is required");
      this.deferredResultInterceptors.put(key, interceptor);
   }

   public void registerDeferredResultInterceptors(DeferredResultProcessingInterceptor... interceptors) {
      Assert.notNull(interceptors, "A DeferredResultProcessingInterceptor is required");

      for (DeferredResultProcessingInterceptor interceptor : interceptors) {
         String key = interceptor.getClass().getName() + ":" + interceptor.hashCode();
         this.deferredResultInterceptors.put(key, interceptor);
      }
   }

   public void setMultipartRequestParsed(boolean isMultipart) {
      this.isMultipartRequestParsed = isMultipart;
   }

   public boolean isMultipartRequestParsed() {
      return this.isMultipartRequestParsed;
   }

   public void clearConcurrentResult() {
      if (!this.state.compareAndSet(WebAsyncManager.State.RESULT_SET, WebAsyncManager.State.NOT_STARTED)) {
         if (logger.isDebugEnabled()) {
            logger.debug("Unexpected call to clear: [" + this.state.get() + "]");
         }
      } else {
         synchronized (this) {
            this.concurrentResult = RESULT_NONE;
            this.concurrentResultContext = null;
         }
      }
   }

   public void startCallableProcessing(Callable<?> callable, Object... processingContext) throws Exception {
      Assert.notNull(callable, "Callable must not be null");
      this.startCallableProcessing(new WebAsyncTask<>(callable), processingContext);
   }

   public void startCallableProcessing(final WebAsyncTask<?> webAsyncTask, Object... processingContext) throws Exception {
      Assert.notNull(webAsyncTask, "WebAsyncTask must not be null");
      Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");
      if (!this.state.compareAndSet(WebAsyncManager.State.NOT_STARTED, WebAsyncManager.State.ASYNC_PROCESSING)) {
         throw new IllegalStateException("Unexpected call to startCallableProcessing: [" + this.state.get() + "]");
      }

      Long timeout = webAsyncTask.getTimeout();
      if (timeout != null) {
         this.asyncWebRequest.setTimeout(timeout);
      }

      AsyncTaskExecutor executor = webAsyncTask.getExecutor();
      if (executor != null) {
         this.taskExecutor = executor;
      }

      List<CallableProcessingInterceptor> interceptors = new ArrayList<>();
      interceptors.add(webAsyncTask.getInterceptor());
      interceptors.addAll(this.callableInterceptors.values());
      interceptors.add(timeoutCallableInterceptor);
      Callable<?> callable = webAsyncTask.getCallable();
      CallableInterceptorChain interceptorChain = new CallableInterceptorChain(interceptors);
      this.asyncWebRequest.addTimeoutHandler(() -> {
         if (logger.isDebugEnabled()) {
            logger.debug("Servlet container timeout notification for " + formatUri(this.asyncWebRequest));
         }

         Object resultx = interceptorChain.triggerAfterTimeout(this.asyncWebRequest, callable);
         if (resultx != CallableProcessingInterceptor.RESULT_NONE) {
            this.setConcurrentResultAndDispatch(resultx);
         }
      });
      this.asyncWebRequest.addErrorHandler(exx -> {
         if (logger.isDebugEnabled()) {
            logger.debug("Servlet container error notification for " + formatUri(this.asyncWebRequest) + ": " + exx);
         }

         if (exx instanceof IOException) {
            exx = new AsyncRequestNotUsableException("Servlet container error notification for disconnected client", exx);
         }

         Object resultx = interceptorChain.triggerAfterError(this.asyncWebRequest, callable, exx);
         resultx = resultx != CallableProcessingInterceptor.RESULT_NONE ? resultx : exx;
         this.setConcurrentResultAndDispatch(resultx);
      });
      this.asyncWebRequest.addCompletionHandler(() -> interceptorChain.triggerAfterCompletion(this.asyncWebRequest, callable));
      interceptorChain.applyBeforeConcurrentHandling(this.asyncWebRequest, callable);
      this.startAsyncProcessing(processingContext);

      try {
         Future<?> future = this.taskExecutor.submit(() -> {
            Object resultx = null;

            try {
               interceptorChain.applyPreProcess(this.asyncWebRequest, callable);
               resultx = callable.call();
            } catch (Throwable ex) {
               resultx = exx;
            } finally {
               resultx = interceptorChain.applyPostProcess(this.asyncWebRequest, callable, resultx);
            }

            this.setConcurrentResultAndDispatch(resultx);
         });
         interceptorChain.setTaskFuture(future);
      } catch (Throwable ex) {
         Object result = interceptorChain.applyPostProcess(this.asyncWebRequest, callable, ex);
         this.setConcurrentResultAndDispatch(result);
      }
   }

   public void startDeferredResultProcessing(final DeferredResult<?> deferredResult, Object... processingContext) throws Exception {
      Assert.notNull(deferredResult, "DeferredResult must not be null");
      Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");
      if (!this.state.compareAndSet(WebAsyncManager.State.NOT_STARTED, WebAsyncManager.State.ASYNC_PROCESSING)) {
         throw new IllegalStateException("Unexpected call to startDeferredResultProcessing: [" + this.state.get() + "]");
      }

      Long timeout = deferredResult.getTimeoutValue();
      if (timeout != null) {
         this.asyncWebRequest.setTimeout(timeout);
      }

      List<DeferredResultProcessingInterceptor> interceptors = new ArrayList<>();
      interceptors.add(deferredResult.getLifecycleInterceptor());
      interceptors.addAll(this.deferredResultInterceptors.values());
      interceptors.add(timeoutDeferredResultInterceptor);
      DeferredResultInterceptorChain interceptorChain = new DeferredResultInterceptorChain(interceptors);
      this.asyncWebRequest.addTimeoutHandler(() -> {
         if (logger.isDebugEnabled()) {
            logger.debug("Servlet container timeout notification for " + formatUri(this.asyncWebRequest));
         }

         try {
            interceptorChain.triggerAfterTimeout(this.asyncWebRequest, deferredResult);
            synchronized (this) {
               ;
            }
         } catch (Throwable ex) {
            this.setConcurrentResultAndDispatch(ex);
         }
      });
      this.asyncWebRequest.addErrorHandler(ex -> {
         if (logger.isDebugEnabled()) {
            logger.debug("Servlet container error notification for " + formatUri(this.asyncWebRequest));
         }

         if (ex instanceof IOException) {
            ex = new AsyncRequestNotUsableException("Servlet container error notification for disconnected client", ex);
         }

         try {
            interceptorChain.triggerAfterError(this.asyncWebRequest, deferredResult, ex);
            synchronized (this) {
               ;
            }
         } catch (Throwable interceptorEx) {
            this.setConcurrentResultAndDispatch(interceptorEx);
         }
      });
      this.asyncWebRequest.addCompletionHandler(() -> interceptorChain.triggerAfterCompletion(this.asyncWebRequest, deferredResult));
      interceptorChain.applyBeforeConcurrentHandling(this.asyncWebRequest, deferredResult);
      this.startAsyncProcessing(processingContext);

      try {
         interceptorChain.applyPreProcess(this.asyncWebRequest, deferredResult);
         deferredResult.setResultHandler(result -> {
            result = interceptorChain.applyPostProcess(this.asyncWebRequest, deferredResult, result);
            this.setConcurrentResultAndDispatch(result);
         });
      } catch (Throwable ex) {
         this.setConcurrentResultAndDispatch(ex);
      }
   }

   private void startAsyncProcessing(Object[] processingContext) {
      synchronized (this) {
         this.concurrentResult = RESULT_NONE;
         this.concurrentResultContext = processingContext;
      }

      Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");
      if (logger.isDebugEnabled()) {
         logger.debug("Started async request for " + formatUri(this.asyncWebRequest));
      }

      this.asyncWebRequest.startAsync();
   }

   private void setConcurrentResultAndDispatch(@Nullable Object result) {
      Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");
      synchronized (this) {
         if (!this.state.compareAndSet(WebAsyncManager.State.ASYNC_PROCESSING, WebAsyncManager.State.RESULT_SET)) {
            if (logger.isDebugEnabled()) {
               logger.debug("Async result already set: [" + this.state.get() + "], ignored result for " + formatUri(this.asyncWebRequest));
            }
         } else {
            this.concurrentResult = result;
            if (logger.isDebugEnabled()) {
               logger.debug("Async result set for " + formatUri(this.asyncWebRequest));
            }

            if (this.asyncWebRequest.isAsyncComplete()) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Async request already completed for " + formatUri(this.asyncWebRequest));
               }
            } else {
               if (logger.isDebugEnabled()) {
                  logger.debug("Performing async dispatch for " + formatUri(this.asyncWebRequest));
               }

               this.asyncWebRequest.dispatch();
            }
         }
      }
   }

   private static String formatUri(AsyncWebRequest asyncWebRequest) {
      HttpServletRequest request = asyncWebRequest.getNativeRequest(HttpServletRequest.class);
      return request != null ? "\"" + request.getRequestURI() + "\"" : "servlet container";
   }

   private enum State {
      NOT_STARTED,
      ASYNC_PROCESSING,
      RESULT_SET;
   }
}
