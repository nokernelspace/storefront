package org.springframework.expression;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.ObjectUtils;

public class TypedValue {
   public static final TypedValue NULL = new TypedValue(null);
   private final @Nullable Object value;
   private @Nullable TypeDescriptor typeDescriptor;

   public TypedValue(@Nullable Object value) {
      this.value = value;
      this.typeDescriptor = null;
   }

   public TypedValue(@Nullable Object value, @Nullable TypeDescriptor typeDescriptor) {
      this.value = value;
      this.typeDescriptor = typeDescriptor;
   }

   public @Nullable Object getValue() {
      return this.value;
   }

   public @Nullable TypeDescriptor getTypeDescriptor() {
      if (this.typeDescriptor == null && this.value != null) {
         this.typeDescriptor = TypeDescriptor.forObject(this.value);
      }

      return this.typeDescriptor;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof TypedValue that
            && ObjectUtils.nullSafeEquals(this.value, that.value)
            && (this.typeDescriptor == null && that.typeDescriptor == null || ObjectUtils.nullSafeEquals(this.getTypeDescriptor(), that.getTypeDescriptor()));
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHashCode(this.value);
   }

   @Override
   public String toString() {
      return "TypedValue: '" + this.value + "' of [" + this.getTypeDescriptor() + "]";
   }
}
