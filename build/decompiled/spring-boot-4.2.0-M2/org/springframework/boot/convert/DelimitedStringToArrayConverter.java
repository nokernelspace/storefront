package org.springframework.boot.convert;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

final class DelimitedStringToArrayConverter implements ConditionalGenericConverter {
   private final ConversionService conversionService;

   DelimitedStringToArrayConverter(ConversionService conversionService) {
      Assert.notNull(conversionService, "'conversionService' must not be null");
      this.conversionService = conversionService;
   }

   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, Object[].class));
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
      Assert.state(elementDescriptor != null, "elementDescriptor is missing");
      Object target = Array.newInstance(elementDescriptor.getType(), elements.length);

      for (int i = 0; i < elements.length; i++) {
         String sourceElement = elements[i];
         Object targetElement = this.conversionService.convert(sourceElement.trim(), sourceType, elementDescriptor);
         Array.set(target, i, targetElement);
      }

      return target;
   }

   private String[] getElements(String source, String delimiter) {
      return StringUtils.delimitedListToStringArray(source, "".equals(delimiter) ? null : delimiter);
   }
}
