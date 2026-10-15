package org.springframework.core.convert.support;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.CollectionUtils;

final class ObjectToOptionalConverter implements ConditionalGenericConverter {
   private final ConversionService conversionService;

   public ObjectToOptionalConverter(ConversionService conversionService) {
      this.conversionService = conversionService;
   }

   @Override
   public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
      Set<GenericConverter.ConvertiblePair> convertibleTypes = CollectionUtils.newLinkedHashSet(3);
      convertibleTypes.add(new GenericConverter.ConvertiblePair(Collection.class, Optional.class));
      convertibleTypes.add(new GenericConverter.ConvertiblePair(Object[].class, Optional.class));
      convertibleTypes.add(new GenericConverter.ConvertiblePair(Object.class, Optional.class));
      return convertibleTypes;
   }

   @Override
   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return targetType.getResolvableType().hasGenerics()
         ? this.conversionService.canConvert(sourceType, new ObjectToOptionalConverter.GenericTypeDescriptor(targetType))
         : true;
   }

   @Override
   public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (source == null) {
         return Optional.empty();
      }

      if (source instanceof Optional) {
         return source;
      }

      if (!targetType.getResolvableType().hasGenerics()) {
         return Optional.of(source);
      }

      Object target = this.conversionService.convert(source, sourceType, new ObjectToOptionalConverter.GenericTypeDescriptor(targetType));
      return target != null
            && (!target.getClass().isArray() || Array.getLength(target) != 0)
            && !(target instanceof Collection<?> collection && collection.isEmpty())
         ? Optional.of(target)
         : Optional.empty();
   }

   private static class GenericTypeDescriptor extends TypeDescriptor {
      public GenericTypeDescriptor(TypeDescriptor typeDescriptor) {
         super(typeDescriptor.getResolvableType().getGeneric(), null, typeDescriptor.getAnnotations());
      }
   }
}
