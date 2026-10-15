package org.springframework.boot.convert;

import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;

class CharSequenceToObjectConverter implements ConditionalGenericConverter {
   private static final TypeDescriptor STRING = TypeDescriptor.valueOf(String.class);
   private static final TypeDescriptor BYTE_ARRAY = TypeDescriptor.valueOf(byte[].class);
   private static final Set<ConvertiblePair> TYPES = Collections.singleton(new ConvertiblePair(CharSequence.class, Object.class));
   private final ThreadLocal<Boolean> disable = new ThreadLocal<>();
   private final ConversionService conversionService;

   CharSequenceToObjectConverter(ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   public Set<ConvertiblePair> getConvertibleTypes() {
      return TYPES;
   }

   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (sourceType.getType() != String.class && this.disable.get() != Boolean.TRUE) {
         this.disable.set(Boolean.TRUE);

         try {
            boolean canDirectlyConvertCharSequence = this.conversionService.canConvert(sourceType, targetType);
            return canDirectlyConvertCharSequence && !this.isStringConversionBetter(sourceType, targetType)
               ? false
               : this.conversionService.canConvert(STRING, targetType);
         } finally {
            this.disable.remove();
         }
      } else {
         return false;
      }
   }

   private boolean isStringConversionBetter(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return this.conversionService instanceof ApplicationConversionService applicationConversionService
            && applicationConversionService.isConvertViaObjectSourceType(sourceType, targetType)
         ? true
         : (targetType.isArray() || targetType.isCollection()) && !targetType.equals(BYTE_ARRAY);
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return this.conversionService.convert(source != null ? source.toString() : null, STRING, targetType);
   }
}
