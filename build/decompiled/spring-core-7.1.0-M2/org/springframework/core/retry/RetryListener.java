package org.springframework.core.retry;

import org.jspecify.annotations.Nullable;

public interface RetryListener {
   default void onRetryableExecution(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
   }

   default void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
      this.beforeRetry(retryPolicy, retryable);
   }

   default void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable) {
   }

   default void onRetrySuccess(RetryPolicy retryPolicy, Retryable<?> retryable, @Nullable Object result) {
   }

   default void onRetryFailure(RetryPolicy retryPolicy, Retryable<?> retryable, Throwable throwable) {
   }

   default void onRetryPolicyExhaustion(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
   }

   default void onRetryPolicyInterruption(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
   }

   default void onRetryPolicyTimeout(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
   }
}
