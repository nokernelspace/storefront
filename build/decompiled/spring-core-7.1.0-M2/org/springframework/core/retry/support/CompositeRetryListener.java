package org.springframework.core.retry.support;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;
import org.springframework.util.Assert;

public class CompositeRetryListener implements RetryListener {
   private final List<RetryListener> listeners = new ArrayList<>();

   public CompositeRetryListener() {
   }

   public CompositeRetryListener(List<RetryListener> listeners) {
      Assert.notEmpty(listeners, "RetryListener list must not be empty");
      this.listeners.addAll(listeners);
   }

   public void addListener(RetryListener listener) {
      this.listeners.add(listener);
   }

   @Override
   public void onRetryableExecution(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
      this.listeners.forEach(listener -> listener.onRetryableExecution(retryPolicy, retryable, retryState));
   }

   @Override
   public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
      this.listeners.forEach(retryListener -> retryListener.beforeRetry(retryPolicy, retryable, retryState));
   }

   @Override
   public void onRetrySuccess(RetryPolicy retryPolicy, Retryable<?> retryable, @Nullable Object result) {
      this.listeners.forEach(listener -> listener.onRetrySuccess(retryPolicy, retryable, result));
   }

   @Override
   public void onRetryFailure(RetryPolicy retryPolicy, Retryable<?> retryable, Throwable throwable) {
      this.listeners.forEach(listener -> listener.onRetryFailure(retryPolicy, retryable, throwable));
   }

   @Override
   public void onRetryPolicyExhaustion(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
      this.listeners.forEach(listener -> listener.onRetryPolicyExhaustion(retryPolicy, retryable, exception));
   }

   @Override
   public void onRetryPolicyInterruption(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
      this.listeners.forEach(listener -> listener.onRetryPolicyInterruption(retryPolicy, retryable, exception));
   }

   @Override
   public void onRetryPolicyTimeout(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
      this.listeners.forEach(listener -> listener.onRetryPolicyTimeout(retryPolicy, retryable, exception));
   }
}
