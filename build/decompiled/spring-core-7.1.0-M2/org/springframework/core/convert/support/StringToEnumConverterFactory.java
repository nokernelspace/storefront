package org.springframework.core.convert.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.util.Assert;

final class StringToEnumConverterFactory implements ConverterFactory<String, Enum>, ConditionalConverter {
   @Override
   public <T extends Enum> Converter<String, @Nullable T> getConverter(Class<T> targetType) {
      Class<?> enumType = ConversionUtils.resolveEnumType(targetType);
      Assert.notNull(enumType, () -> "The target type " + targetType.getName() + " does not refer to an enum");
      return new StringToEnumConverterFactory.StringToEnum<>((Class<T>)enumType);
   }

   @Override
   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return ConversionUtils.resolveEnumType(targetType.getType()) != null;
   }

   private static class StringToEnum<T extends Enum> implements Converter<String, T> {
      private final Class<T> enumType;

      StringToEnum(Class<T> enumType) {
         this.enumType = enumType;
      }

      public @Nullable T convert(String source) {
         return source.isEmpty() ? null : Enum.valueOf(this.enumType, source.trim());
      }
   }
}
