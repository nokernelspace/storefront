package org.springframework.core.convert.support;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;

final class FallbackObjectToStringConverter implements ConditionalGenericConverter {
   @Override
   public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new GenericConverter.ConvertiblePair(Object.class, String.class));
   }

   @Override
   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      Class<?> sourceClass = sourceType.getObjectType();
      return String.class == sourceClass
         ? false
         : CharSequence.class.isAssignableFrom(sourceClass)
            || StringWriter.class.isAssignableFrom(sourceClass)
            || ObjectToObjectConverter.hasConversionMethodOrConstructor(sourceClass, String.class);
   }

   @Override
   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return source != null ? source.toString() : null;
   }
}
