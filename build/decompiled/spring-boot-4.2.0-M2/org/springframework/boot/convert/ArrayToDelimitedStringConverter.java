package org.springframework.boot.convert;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.util.ObjectUtils;

final class ArrayToDelimitedStringConverter implements ConditionalGenericConverter {
   private final CollectionToDelimitedStringConverter delegate;

   ArrayToDelimitedStringConverter(ConversionService conversionService) {
      this.delegate = new CollectionToDelimitedStringConverter(conversionService);
   }

   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(Object[].class, String.class));
   }

   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return this.delegate.matches(sourceType, targetType);
   }

   public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      List<Object> list = Arrays.asList(ObjectUtils.toObjectArray(source));
      return this.delegate.convert(list, sourceType, targetType);
   }
}
