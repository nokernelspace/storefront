package org.springframework.core.convert.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.ClassUtils;

abstract class ConversionUtils {
   public static @Nullable Object invokeConverter(GenericConverter converter, @Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      try {
         return converter.convert(source, sourceType, targetType);
      } catch (ConversionFailedException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new ConversionFailedException(sourceType, targetType, source, ex);
      }
   }

   public static boolean canConvertElements(
      @Nullable TypeDescriptor sourceElementType, @Nullable TypeDescriptor targetElementType, ConversionService conversionService
   ) {
      if (targetElementType == null) {
         return true;
      } else if (sourceElementType == null) {
         return true;
      } else {
         return conversionService.canConvert(sourceElementType, targetElementType)
            ? true
            : ClassUtils.isAssignable(sourceElementType.getType(), targetElementType.getType());
      }
   }

   public static @Nullable Class<?> resolveEnumType(Class<?> targetType) {
      Class<?> enumType = targetType;

      while (enumType != null && !enumType.isEnum()) {
         enumType = enumType.getSuperclass();
      }

      return enumType;
   }
}
