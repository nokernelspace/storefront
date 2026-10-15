package org.springframework.boot.convert;

import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.util.ObjectUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

final class StringToDataSizeConverter implements GenericConverter {
   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, DataSize.class));
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return ObjectUtils.isEmpty(source) ? null : this.convert(source.toString(), this.getDataUnit(targetType));
   }

   private @Nullable DataUnit getDataUnit(TypeDescriptor targetType) {
      DataSizeUnit annotation = (DataSizeUnit)targetType.getAnnotation(DataSizeUnit.class);
      return annotation != null ? annotation.value() : null;
   }

   private DataSize convert(String source, @Nullable DataUnit unit) {
      return DataSize.parse(source, unit);
   }
}
