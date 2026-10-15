package org.springframework.beans.factory.aot;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

@FunctionalInterface
public interface AutowiredArguments {
   default <T> @Nullable T get(int index, Class<T> requiredType) {
      Object value = this.getObject(index);
      if (!ClassUtils.isAssignableValue(requiredType, value)) {
         throw new IllegalArgumentException("Argument type mismatch: expected '" + requiredType.getTypeName() + "' for value [" + value + "]");
      } else {
         return (T)value;
      }
   }

   default <T> @Nullable T get(int index) {
      return (T)this.getObject(index);
   }

   default @Nullable Object getObject(int index) {
      return this.toArray()[index];
   }

   @Nullable Object[] toArray();

   static AutowiredArguments of(@Nullable Object[] arguments) {
      Assert.notNull(arguments, "'arguments' must not be null");
      return () -> arguments;
   }
}
