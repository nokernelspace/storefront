package org.springframework.core.convert;

import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;

public class ConversionFailedException extends ConversionException {
   private final @Nullable TypeDescriptor sourceType;
   private final TypeDescriptor targetType;
   private final @Nullable Object value;

   public ConversionFailedException(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType, @Nullable Object value, Throwable cause) {
      super(
         "Failed to convert from type [" + sourceType + "] to type [" + targetType + "] for value [" + ObjectUtils.nullSafeConciseToString(value) + "]", cause
      );
      this.sourceType = sourceType;
      this.targetType = targetType;
      this.value = value;
   }

   public @Nullable TypeDescriptor getSourceType() {
      return this.sourceType;
   }

   public TypeDescriptor getTargetType() {
      return this.targetType;
   }

   public @Nullable Object getValue() {
      return this.value;
   }
}
