package org.springframework.core.convert;

import org.jspecify.annotations.Nullable;

public interface ConversionService {
   boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType);

   boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType);

   <T> @Nullable T convert(@Nullable Object source, Class<T> targetType);

   default @Nullable Object convert(@Nullable Object source, TypeDescriptor targetType) {
      return this.convert(source, TypeDescriptor.forObject(source), targetType);
   }

   @Nullable Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType);
}
