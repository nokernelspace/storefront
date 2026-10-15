package org.springframework.util.function;

import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
   R applyWithException(T t) throws Exception;

   @Override
   default R apply(T t) {
      return this.apply(t, RuntimeException::new);
   }

   default R apply(T t, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      try {
         return this.applyWithException(t);
      } catch (RuntimeException ex) {
         throw ex;
      } catch (Exception ex) {
         throw (RuntimeException)exceptionWrapper.apply(ex.getMessage(), ex);
      }
   }

   default ThrowingFunction<T, R> throwing(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return new ThrowingFunction<T, R>() {
         @Override
         public R applyWithException(T t) throws Exception {
            return (R)ThrowingFunction.this.applyWithException(t);
         }

         @Override
         public R apply(T t) {
            return this.apply(t, exceptionWrapper);
         }
      };
   }

   static <T, R> ThrowingFunction<T, R> of(ThrowingFunction<T, R> function) {
      return function;
   }

   static <T, R> ThrowingFunction<T, R> of(ThrowingFunction<T, R> function, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return function.throwing(exceptionWrapper);
   }
}
