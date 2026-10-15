package org.springframework.util.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

@Deprecated(since = "6.0")
public abstract class FutureAdapter<T, S> implements Future<T> {
   private final Future<S> adaptee;
   private @Nullable Object result;
   private FutureAdapter.State state = FutureAdapter.State.NEW;
   private final Object mutex = new Object();

   protected FutureAdapter(Future<S> adaptee) {
      Assert.notNull(adaptee, "Delegate must not be null");
      this.adaptee = adaptee;
   }

   protected Future<S> getAdaptee() {
      return this.adaptee;
   }

   @Override
   public boolean cancel(boolean mayInterruptIfRunning) {
      return this.adaptee.cancel(mayInterruptIfRunning);
   }

   @Override
   public boolean isCancelled() {
      return this.adaptee.isCancelled();
   }

   @Override
   public boolean isDone() {
      return this.adaptee.isDone();
   }

   @Override
   public @Nullable T get() throws InterruptedException, ExecutionException {
      return this.adaptInternal(this.adaptee.get());
   }

   @Override
   public @Nullable T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return this.adaptInternal(this.adaptee.get(timeout, unit));
   }

   final @Nullable T adaptInternal(S adapteeResult) throws ExecutionException {
      synchronized (this.mutex) {
         return (T)(switch (this.state) {
            case NEW -> {
               Object var10;
               try {
                  T adapted = this.adapt(adapteeResult);
                  this.result = adapted;
                  this.state = FutureAdapter.State.SUCCESS;
                  var10 = adapted;
               } catch (ExecutionException ex) {
                  this.result = ex;
                  this.state = FutureAdapter.State.FAILURE;
                  throw ex;
               } catch (Throwable ex) {
                  ExecutionException execEx = new ExecutionException(ex);
                  this.result = execEx;
                  this.state = FutureAdapter.State.FAILURE;
                  throw execEx;
               }

               yield var10;
            }
            case SUCCESS -> {
               Object var3 = this.result;
               yield var3;
            }
            case FAILURE -> {
               Assert.state(this.result instanceof ExecutionException, "Failure without exception");
               throw (ExecutionException)this.result;
            }
         });
      }
   }

   protected abstract @Nullable T adapt(S adapteeResult) throws ExecutionException;

   private enum State {
      NEW,
      SUCCESS,
      FAILURE;
   }
}
