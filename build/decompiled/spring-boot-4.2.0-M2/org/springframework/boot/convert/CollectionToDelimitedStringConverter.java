package org.springframework.boot.convert;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.lang.Contract;

final class CollectionToDelimitedStringConverter implements ConditionalGenericConverter {
   private final ConversionService conversionService;

   CollectionToDelimitedStringConverter(ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(Collection.class, String.class));
   }

   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      TypeDescriptor sourceElementType = sourceType.getElementTypeDescriptor();
      return targetType != null && sourceElementType != null
         ? this.conversionService.canConvert(sourceElementType, targetType) || sourceElementType.getType().isAssignableFrom(targetType.getType())
         : true;
   }

   @Contract("!null, _, _ -> !null")
   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (source == null) {
         return null;
      }

      Collection<?> sourceCollection = (Collection<?>)source;
      return this.convert(sourceCollection, sourceType, targetType);
   }

   private Object convert(Collection<?> source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return source.isEmpty()
         ? ""
         : source.stream().map(element -> this.convertElement(element, sourceType, targetType)).collect(Collectors.joining(this.getDelimiter(sourceType)));
   }

   private CharSequence getDelimiter(TypeDescriptor sourceType) {
      Delimiter annotation = (Delimiter)sourceType.getAnnotation(Delimiter.class);
      return annotation != null ? annotation.value() : ",";
   }

   private String convertElement(Object element, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return String.valueOf(this.conversionService.convert(element, sourceType.elementTypeDescriptor(element), targetType));
   }
}
