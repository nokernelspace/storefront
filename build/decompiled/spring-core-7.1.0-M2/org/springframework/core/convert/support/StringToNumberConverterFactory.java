package org.springframework.core.convert.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.util.NumberUtils;

final class StringToNumberConverterFactory implements ConverterFactory<String, Number> {
   @Override
   public <T extends Number> Converter<String, @Nullable T> getConverter(Class<T> targetType) {
      return new StringToNumberConverterFactory.StringToNumber<>(targetType);
   }

   private static final class StringToNumber<T extends Number> implements Converter<String, T> {
      private final Class<T> targetType;

      public StringToNumber(Class<T> targetType) {
         this.targetType = targetType;
      }

      public @Nullable T convert(String source) {
         return source.isEmpty() ? null : NumberUtils.parseNumber(source, this.targetType);
      }
   }
}
