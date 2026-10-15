package org.springframework.boot.convert;

import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.util.ObjectUtils;

final class PeriodToStringConverter implements GenericConverter {
   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(Period.class, String.class));
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return ObjectUtils.isEmpty(source) ? null : this.convert((Period)source, this.getPeriodStyle(sourceType), this.getPeriodUnit(sourceType));
   }

   private @Nullable PeriodStyle getPeriodStyle(TypeDescriptor sourceType) {
      PeriodFormat annotation = (PeriodFormat)sourceType.getAnnotation(PeriodFormat.class);
      return annotation != null ? annotation.value() : null;
   }

   private String convert(Period source, @Nullable PeriodStyle style, @Nullable ChronoUnit unit) {
      style = style != null ? style : PeriodStyle.ISO8601;
      return style.print(source, unit);
   }

   private @Nullable ChronoUnit getPeriodUnit(TypeDescriptor sourceType) {
      PeriodUnit annotation = (PeriodUnit)sourceType.getAnnotation(PeriodUnit.class);
      return annotation != null ? annotation.value() : null;
   }
}
