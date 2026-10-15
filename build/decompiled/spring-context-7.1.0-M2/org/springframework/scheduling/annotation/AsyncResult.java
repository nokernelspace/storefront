package org.springframework.scheduling.annotation;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

@Deprecated(since = "6.0")
public class AsyncResult<V> implements Future<V> {
   private final @Nullable V value;
   private final @Nullable Throwable executionException;

   public AsyncResult(@Nullable V value) {
      this(value, null);
   }

   private AsyncResult(@Nullable V value, @Nullable Throwable ex) {
      this.value = value;
      this.executionException = ex;
   }

   @Override
   public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
   }

   @Override
   public boolean isCancelled() {
      return false;
   }

   @Override
   public boolean isDone() {
      return true;
   }

   @Override
   public @Nullable V get() throws ExecutionException {
      if (this.executionException != null) {
         throw this.executionException instanceof ExecutionException execEx ? execEx : new ExecutionException(this.executionException);
      } else {
         return this.value;
      }
   }

   @Override
   public @Nullable V get(long timeout, TimeUnit unit) throws ExecutionException {
      return this.get();
   }

   public static <V> Future<V> forValue(V value) {
      return new AsyncResult<>(value, null);
   }

   public static <V> Future<V> forExecutionException(Throwable ex) {
      return new AsyncResult<>(null, ex);
   }
}
