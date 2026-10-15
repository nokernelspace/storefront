package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.function.ThrowingBiFunction;
import org.springframework.util.function.ThrowingSupplier;

@FunctionalInterface
public interface InstanceSupplier<T> extends ThrowingSupplier<T> {
   default T getWithException() {
      throw new IllegalStateException("No RegisteredBean parameter provided");
   }

   T get(RegisteredBean registeredBean) throws Exception;

   default @Nullable Method getFactoryMethod() {
      return null;
   }

   default <V> InstanceSupplier<V> andThen(ThrowingBiFunction<RegisteredBean, ? super T, ? extends V> after) {
      Assert.notNull(after, "'after' function must not be null");
      return new InstanceSupplier<V>() {
         @Override
         public V get(RegisteredBean registeredBean) throws Exception {
            return (V)after.applyWithException(registeredBean, InstanceSupplier.this.get(registeredBean));
         }

         @Override
         public @Nullable Method getFactoryMethod() {
            return InstanceSupplier.this.getFactoryMethod();
         }
      };
   }

   static <T> InstanceSupplier<T> using(ThrowingSupplier<T> supplier) {
      Assert.notNull(supplier, "Supplier must not be null");
      return supplier instanceof InstanceSupplier<T> instanceSupplier ? instanceSupplier : registeredBean -> (T)supplier.getWithException();
   }

   static <T> InstanceSupplier<T> using(@Nullable Method factoryMethod, ThrowingSupplier<T> supplier) {
      Assert.notNull(supplier, "Supplier must not be null");
      return supplier instanceof InstanceSupplier<T> instanceSupplier && instanceSupplier.getFactoryMethod() == factoryMethod
         ? instanceSupplier
         : new InstanceSupplier<T>() {
            @Override
            public T get(RegisteredBean registeredBean) throws Exception {
               return (T)supplier.getWithException();
            }

            @Override
            public @Nullable Method getFactoryMethod() {
               return factoryMethod;
            }
         };
   }

   static <T> InstanceSupplier<T> of(InstanceSupplier<T> instanceSupplier) {
      Assert.notNull(instanceSupplier, "InstanceSupplier must not be null");
      return instanceSupplier;
   }
}
