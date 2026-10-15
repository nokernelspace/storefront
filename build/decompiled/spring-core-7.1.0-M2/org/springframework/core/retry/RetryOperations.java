package org.springframework.core.retry;

import java.util.function.Supplier;

public interface RetryOperations {
   <R> R execute(Retryable<R> retryable) throws RetryException;

   <R> R invoke(Supplier<R> retryable);

   void invoke(Runnable retryable);
}
