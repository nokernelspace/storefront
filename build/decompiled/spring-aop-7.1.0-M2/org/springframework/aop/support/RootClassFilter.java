package org.springframework.aop.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.util.Assert;

public class RootClassFilter implements ClassFilter, Serializable {
   private final Class<?> clazz;

   public RootClassFilter(Class<?> clazz) {
      Assert.notNull(clazz, "Class must not be null");
      this.clazz = clazz;
   }

   @Override
   public boolean matches(Class<?> candidate) {
      return this.clazz.isAssignableFrom(candidate);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof RootClassFilter that && this.clazz.equals(that.clazz);
   }

   @Override
   public int hashCode() {
      return this.clazz.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.clazz.getName();
   }
}
