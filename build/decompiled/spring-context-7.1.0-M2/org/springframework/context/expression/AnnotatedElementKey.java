package org.springframework.context.expression;

import java.lang.reflect.AnnotatedElement;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public final class AnnotatedElementKey implements Comparable<AnnotatedElementKey> {
   private final AnnotatedElement element;
   private final @Nullable Class<?> targetClass;

   public AnnotatedElementKey(AnnotatedElement element, @Nullable Class<?> targetClass) {
      Assert.notNull(element, "AnnotatedElement must not be null");
      this.element = element;
      this.targetClass = targetClass;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof AnnotatedElementKey that && this.element.equals(that.element) && ObjectUtils.nullSafeEquals(this.targetClass, that.targetClass);
   }

   @Override
   public int hashCode() {
      return this.element.hashCode() + (this.targetClass != null ? this.targetClass.hashCode() * 29 : 0);
   }

   @Override
   public String toString() {
      return this.element + (this.targetClass != null ? " on " + this.targetClass : "");
   }

   public int compareTo(AnnotatedElementKey other) {
      int result = this.element.toString().compareTo(other.element.toString());
      if (result == 0 && this.targetClass != null) {
         if (other.targetClass == null) {
            return 1;
         }

         result = this.targetClass.getName().compareTo(other.targetClass.getName());
      }

      return result;
   }
}
