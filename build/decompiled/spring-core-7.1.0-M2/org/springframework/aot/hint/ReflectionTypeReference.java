package org.springframework.aot.hint;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class ReflectionTypeReference extends AbstractTypeReference {
   private final Class<?> type;

   private ReflectionTypeReference(Class<?> type) {
      super(type.getPackageName(), type.getSimpleName(), getEnclosingClass(type));
      this.type = type;
   }

   private static @Nullable TypeReference getEnclosingClass(Class<?> type) {
      Class<?> candidate = type.isArray() ? type.componentType().getEnclosingClass() : type.getEnclosingClass();
      return candidate != null ? new ReflectionTypeReference(candidate) : null;
   }

   static ReflectionTypeReference of(Class<?> type) {
      Assert.notNull(type, "'type' must not be null");
      Assert.notNull(type.getCanonicalName(), "'type.getCanonicalName()' must not be null");
      return new ReflectionTypeReference(type);
   }

   @Override
   public String getCanonicalName() {
      return this.type.getCanonicalName();
   }

   @Override
   protected boolean isPrimitive() {
      return this.type.isPrimitive() || this.type.isArray() && this.type.componentType().isPrimitive();
   }
}
