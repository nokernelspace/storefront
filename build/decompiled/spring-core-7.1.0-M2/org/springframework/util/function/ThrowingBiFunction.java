package org.springframework.util.function;

import java.util.function.BiFunction;

public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {
   R applyWithException(T t, U u) throws Exception;

   @Override
   default R apply(T t, U u) {
      return this.apply(t, u, RuntimeException::new);
   }

   default R apply(T t, U u, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      try {
         return this.applyWithException(t, u);
      } catch (RuntimeException ex) {
         throw ex;
      } catch (Exception ex) {
         throw (RuntimeException)exceptionWrapper.apply(ex.getMessage(), ex);
      }
   }

   default ThrowingBiFunction<T, U, R> throwing(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return new ThrowingBiFunction<T, U, R>() {
         @Override
         public R applyWithException(T t, U u) throws Exception {
            return (R)ThrowingBiFunction.this.applyWithException(t, u);
         }

         @Override
         public R apply(T t, U u) {
            return this.apply((U)t, u, exceptionWrapper);
         }
      };
   }

   static <T, U, R> ThrowingBiFunction<T, U, R> of(ThrowingBiFunction<T, U, R> function) {
      return function;
   }

   static <T, U, R> ThrowingBiFunction<T, U, R> of(ThrowingBiFunction<T, U, R> function, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return function.throwing(exceptionWrapper);
   }
}
