package org.springframework.util.function;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {
   void acceptWithException(T t) throws Exception;

   @Override
   default void accept(T t) {
      this.accept(t, RuntimeException::new);
   }

   default void accept(T t, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      try {
         this.acceptWithException(t);
      } catch (RuntimeException ex) {
         throw ex;
      } catch (Exception ex) {
         throw (RuntimeException)exceptionWrapper.apply(ex.getMessage(), ex);
      }
   }

   default ThrowingConsumer<T> throwing(BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return new ThrowingConsumer<T>() {
         @Override
         public void acceptWithException(T t) throws Exception {
            ThrowingConsumer.this.acceptWithException(t);
         }

         @Override
         public void accept(T t) {
            this.accept(t, exceptionWrapper);
         }
      };
   }

   static <T> ThrowingConsumer<T> of(ThrowingConsumer<T> consumer) {
      return consumer;
   }

   static <T> ThrowingConsumer<T> of(ThrowingConsumer<T> consumer, BiFunction<String, Exception, RuntimeException> exceptionWrapper) {
      return consumer.throwing(exceptionWrapper);
   }
}
