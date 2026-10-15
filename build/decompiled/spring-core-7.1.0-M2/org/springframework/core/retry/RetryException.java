package org.springframework.core.retry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RetryException extends Exception implements RetryState {
   private static final long serialVersionUID = 1L;

   public RetryException(String message, Throwable cause) {
      super(message, Objects.requireNonNull(cause, "cause must not be null"));
   }

   RetryException(String message, RetryState retryState) {
      super(message, retryState.getLastException());
      List<Throwable> exceptions = retryState.getExceptions();

      for (int i = 0; i < exceptions.size() - 1; i++) {
         this.addSuppressed(exceptions.get(i));
      }
   }

   @Override
   public final Throwable getCause() {
      return Objects.requireNonNull(super.getCause());
   }

   @Override
   public int getRetryCount() {
      return this.getSuppressed().length;
   }

   @Override
   public List<Throwable> getExceptions() {
      Throwable[] suppressed = this.getSuppressed();
      List<Throwable> exceptions = new ArrayList<>(suppressed.length + 1);
      Collections.addAll(exceptions, suppressed);
      exceptions.add(this.getCause());
      return Collections.unmodifiableList(exceptions);
   }

   @Override
   public Throwable getLastException() {
      return this.getCause();
   }
}
