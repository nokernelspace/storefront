package org.springframework.core.convert.support;

import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;

final class OptionalToObjectConverter implements ConditionalGenericConverter {
   private final ConversionService conversionService;

   OptionalToObjectConverter(ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   @Override
   public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
      return Set.of(new GenericConverter.ConvertiblePair(Optional.class, Object.class));
   }

   @Override
   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      ResolvableType elementType = sourceType.getResolvableType().getGeneric();
      if (elementType.resolve() == null) {
         return true;
      }

      TypeDescriptor sourceElementType = new TypeDescriptor(elementType, null, null);
      return ConversionUtils.canConvertElements(sourceElementType, targetType, this.conversionService);
   }

   @Override
   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (source == null) {
         return null;
      }

      Optional<?> optional = (Optional<?>)source;
      Object unwrappedSource = optional.orElse(null);
      TypeDescriptor unwrappedSourceType = TypeDescriptor.forObject(unwrappedSource);
      return this.conversionService.convert(unwrappedSource, unwrappedSourceType, targetType);
   }
}
