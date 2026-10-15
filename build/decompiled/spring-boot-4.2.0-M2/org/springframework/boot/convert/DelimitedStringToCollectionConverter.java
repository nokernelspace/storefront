package org.springframework.boot.convert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

final class DelimitedStringToCollectionConverter implements ConditionalGenericConverter {
   private final ConversionService conversionService;

   DelimitedStringToCollectionConverter(ConversionService conversionService) {
      Assert.notNull(conversionService, "'conversionService' must not be null");
      this.conversionService = conversionService;
   }

   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, Collection.class));
   }

   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return targetType.getElementTypeDescriptor() == null || this.conversionService.canConvert(sourceType, targetType.getElementTypeDescriptor());
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return source == null ? null : this.convert((String)source, sourceType, targetType);
   }

   private Object convert(String source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      Delimiter delimiter = (Delimiter)targetType.getAnnotation(Delimiter.class);
      String[] elements = this.getElements(source, delimiter != null ? delimiter.value() : ",");
      TypeDescriptor elementDescriptor = targetType.getElementTypeDescriptor();
      Collection<Object> target = this.createCollection(targetType, elementDescriptor, elements.length);
      Stream<Object> stream = Arrays.stream(elements).map(String::trim);
      if (elementDescriptor != null) {
         stream = stream.map(element -> this.conversionService.convert(element, sourceType, elementDescriptor));
      }

      stream.forEach(target::add);
      return target;
   }

   private Collection<Object> createCollection(TypeDescriptor targetType, @Nullable TypeDescriptor elementDescriptor, int length) {
      return CollectionFactory.createCollection(targetType.getType(), elementDescriptor != null ? elementDescriptor.getType() : null, length);
   }

   private String[] getElements(String source, String delimiter) {
      return StringUtils.delimitedListToStringArray(source, "".equals(delimiter) ? null : delimiter);
   }
}
