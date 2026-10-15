package org.springframework.expression;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;

public interface TypeConverter {
   boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType);

   @Nullable Object convertValue(@Nullable Object value, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType);
}
