package org.springframework.core;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public interface AttributeAccessor {
   void setAttribute(String name, @Nullable Object value);

   @Nullable Object getAttribute(String name);

   default <T> T computeAttribute(String name, Function<String, T> computeFunction) {
      Assert.notNull(name, "Name must not be null");
      Assert.notNull(computeFunction, "Compute function must not be null");
      Object value = this.getAttribute(name);
      if (value == null) {
         value = computeFunction.apply(name);
         Assert.state(value != null, () -> String.format("Compute function must not return null for attribute named '%s'", name));
         this.setAttribute(name, value);
      }

      return (T)value;
   }

   @Nullable Object removeAttribute(String name);

   boolean hasAttribute(String name);

   String[] attributeNames();
}
