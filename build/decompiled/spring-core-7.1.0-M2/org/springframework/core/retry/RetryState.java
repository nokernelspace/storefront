package org.springframework.core.retry;

import java.util.List;

public interface RetryState {
   int getRetryCount();

   List<Throwable> getExceptions();

   default Throwable getLastException() {
      List<Throwable> exceptions = this.getExceptions();
      if (exceptions.isEmpty()) {
         throw new IllegalStateException("No exception recorded");
      } else {
         return exceptions.get(exceptions.size() - 1);
      }
   }

   default boolean isSuccessful() {
      return this.getRetryCount() >= this.getExceptions().size();
   }
}
