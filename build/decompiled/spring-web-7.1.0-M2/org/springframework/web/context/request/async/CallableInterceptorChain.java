package org.springframework.web.context.request.async;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.NativeWebRequest;

class CallableInterceptorChain {
   private static final Log logger = LogFactory.getLog(CallableInterceptorChain.class);
   private final List<CallableProcessingInterceptor> interceptors;
   private int preProcessIndex = -1;
   private volatile @Nullable Future<?> taskFuture;

   public CallableInterceptorChain(List<CallableProcessingInterceptor> interceptors) {
      this.interceptors = interceptors;
   }

   public void setTaskFuture(Future<?> taskFuture) {
      this.taskFuture = taskFuture;
   }

   public void applyBeforeConcurrentHandling(NativeWebRequest request, Callable<?> task) throws Exception {
      for (CallableProcessingInterceptor interceptor : this.interceptors) {
         interceptor.beforeConcurrentHandling(request, task);
      }
   }

   public void applyPreProcess(NativeWebRequest request, Callable<?> task) throws Exception {
      for (CallableProcessingInterceptor interceptor : this.interceptors) {
         interceptor.preProcess(request, task);
         this.preProcessIndex++;
      }
   }

   public @Nullable Object applyPostProcess(NativeWebRequest request, Callable<?> task, @Nullable Object concurrentResult) {
      Throwable exceptionResult = null;

      for (int i = this.preProcessIndex; i >= 0; i--) {
         try {
            this.interceptors.get(i).postProcess(request, task, concurrentResult);
         } catch (Throwable ex) {
            if (exceptionResult != null) {
               if (logger.isTraceEnabled()) {
                  logger.trace("Ignoring failure in postProcess method", ex);
               }
            } else {
               exceptionResult = ex;
            }
         }
      }

      return exceptionResult != null ? exceptionResult : concurrentResult;
   }

   public Object triggerAfterTimeout(NativeWebRequest request, Callable<?> task) {
      this.cancelTask();

      for (CallableProcessingInterceptor interceptor : this.interceptors) {
         try {
            Object result = interceptor.handleTimeout(request, task);
            if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
               break;
            }

            if (result != CallableProcessingInterceptor.RESULT_NONE) {
               return result;
            }
         } catch (Throwable ex) {
            return ex;
         }
      }

      return CallableProcessingInterceptor.RESULT_NONE;
   }

   private void cancelTask() {
      Future<?> future = this.taskFuture;
      if (future != null) {
         try {
            future.cancel(true);
         } catch (Throwable var3) {
         }
      }
   }

   public Object triggerAfterError(NativeWebRequest request, Callable<?> task, Throwable throwable) {
      this.cancelTask();

      for (CallableProcessingInterceptor interceptor : this.interceptors) {
         try {
            Object result = interceptor.handleError(request, task, throwable);
            if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
               break;
            }

            if (result != CallableProcessingInterceptor.RESULT_NONE) {
               return result;
            }
         } catch (Throwable ex) {
            return ex;
         }
      }

      return CallableProcessingInterceptor.RESULT_NONE;
   }

   public void triggerAfterCompletion(NativeWebRequest request, Callable<?> task) {
      for (int i = this.interceptors.size() - 1; i >= 0; i--) {
         try {
            this.interceptors.get(i).afterCompletion(request, task);
         } catch (Throwable ex) {
            if (logger.isTraceEnabled()) {
               logger.trace("Ignoring failure in afterCompletion method", ex);
            }
         }
      }
   }
}
