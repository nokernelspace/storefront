package org.springframework.web.context.request.async;

import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;

public class WebAsyncTask<V> implements BeanFactoryAware {
   private final Callable<V> callable;
   private final @Nullable Long timeout;
   private final @Nullable AsyncTaskExecutor executor;
   private final @Nullable String executorName;
   private @Nullable BeanFactory beanFactory;
   private @Nullable Callable<V> timeoutCallback;
   private @Nullable Callable<V> errorCallback;
   private @Nullable Runnable completionCallback;

   public WebAsyncTask(Callable<V> callable) {
      Assert.notNull(callable, "Callable must not be null");
      this.callable = callable;
      this.timeout = null;
      this.executor = null;
      this.executorName = null;
   }

   public WebAsyncTask(long timeout, Callable<V> callable) {
      Assert.notNull(callable, "Callable must not be null");
      this.callable = callable;
      this.timeout = timeout;
      this.executor = null;
      this.executorName = null;
   }

   public WebAsyncTask(@Nullable Long timeout, String executorName, Callable<V> callable) {
      Assert.notNull(callable, "Callable must not be null");
      Assert.notNull(executorName, "Executor name must not be null");
      this.callable = callable;
      this.timeout = timeout;
      this.executor = null;
      this.executorName = executorName;
   }

   public WebAsyncTask(@Nullable Long timeout, AsyncTaskExecutor executor, Callable<V> callable) {
      Assert.notNull(callable, "Callable must not be null");
      Assert.notNull(executor, "Executor must not be null");
      this.callable = callable;
      this.timeout = timeout;
      this.executor = executor;
      this.executorName = null;
   }

   public Callable<?> getCallable() {
      return this.callable;
   }

   public @Nullable Long getTimeout() {
      return this.timeout;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   public @Nullable AsyncTaskExecutor getExecutor() {
      if (this.executor != null) {
         return this.executor;
      } else if (this.executorName != null) {
         Assert.state(this.beanFactory != null, "BeanFactory is required to look up an executor bean by name");
         return (AsyncTaskExecutor)this.beanFactory.getBean(this.executorName, AsyncTaskExecutor.class);
      } else {
         return null;
      }
   }

   public void onTimeout(Callable<V> callback) {
      this.timeoutCallback = callback;
   }

   public void onError(Callable<V> callback) {
      this.errorCallback = callback;
   }

   public void onCompletion(Runnable callback) {
      this.completionCallback = callback;
   }

   CallableProcessingInterceptor getInterceptor() {
      return new CallableProcessingInterceptor() {
         @Override
         public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) throws Exception {
            return WebAsyncTask.this.timeoutCallback != null ? WebAsyncTask.this.timeoutCallback.call() : CallableProcessingInterceptor.RESULT_NONE;
         }

         @Override
         public <T> Object handleError(NativeWebRequest request, Callable<T> task, Throwable t) throws Exception {
            return WebAsyncTask.this.errorCallback != null ? WebAsyncTask.this.errorCallback.call() : CallableProcessingInterceptor.RESULT_NONE;
         }

         @Override
         public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) throws Exception {
            if (WebAsyncTask.this.completionCallback != null) {
               WebAsyncTask.this.completionCallback.run();
            }
         }
      };
   }
}
