package org.springframework.core;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;

public final class MethodClassKey implements Comparable<MethodClassKey> {
   private final Method method;
   private final @Nullable Class<?> targetClass;

   public MethodClassKey(Method method, @Nullable Class<?> targetClass) {
      this.method = method;
      this.targetClass = targetClass;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof MethodClassKey that && this.method.equals(that.method) && ObjectUtils.nullSafeEquals(this.targetClass, that.targetClass);
   }

   @Override
   public int hashCode() {
      return this.method.hashCode() + (this.targetClass != null ? this.targetClass.hashCode() * 29 : 0);
   }

   @Override
   public String toString() {
      return this.method + (this.targetClass != null ? " on " + this.targetClass : "");
   }

   public int compareTo(MethodClassKey other) {
      int result = this.method.getName().compareTo(other.method.getName());
      if (result == 0) {
         result = this.method.toString().compareTo(other.method.toString());
         if (result == 0 && this.targetClass != null && other.targetClass != null) {
            result = this.targetClass.getName().compareTo(other.targetClass.getName());
         }
      }

      return result;
   }
}
