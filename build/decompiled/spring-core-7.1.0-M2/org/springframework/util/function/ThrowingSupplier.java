package org.springframework.util.function;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public interface ThrowingSupplier<T> extends Supplier<T> {
   T getWithException() throws Exception;

   @Override
   default T get() {
      return this.get(RuntimeException::new);
   }

   default T get(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      try {
         return this.getWithException();
      } catch (RuntimeException ex) {
         throw ex;
      } catch (Exception ex) {
         throw (RuntimeException)exceptionWrapper.apply(ex.getMessage(), ex);
      }
   }

   default ThrowingSupplier<T> throwing(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return new ThrowingSupplier<T>() {
         @Override
         public T getWithException() throws Exception {
            return (T)ThrowingSupplier.this.getWithException();
         }

         @Override
         public T get() {
            return (T)this.get(exceptionWrapper);
         }
      };
   }

   static <T> ThrowingSupplier<T> of(ThrowingSupplier<T> supplier) {
      return supplier;
   }

   static <T> ThrowingSupplier<T> of(ThrowingSupplier<T> supplier, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return supplier.throwing(exceptionWrapper);
   }
}
