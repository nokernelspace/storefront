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

final class StringToPeriodConverter implements GenericConverter {
   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, Period.class));
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return ObjectUtils.isEmpty(source) ? null : this.convert(source.toString(), this.getStyle(targetType), this.getPeriodUnit(targetType));
   }

   private @Nullable PeriodStyle getStyle(TypeDescriptor targetType) {
      PeriodFormat annotation = (PeriodFormat)targetType.getAnnotation(PeriodFormat.class);
      return annotation != null ? annotation.value() : null;
   }

   private @Nullable ChronoUnit getPeriodUnit(TypeDescriptor targetType) {
      PeriodUnit annotation = (PeriodUnit)targetType.getAnnotation(PeriodUnit.class);
      return annotation != null ? annotation.value() : null;
   }

   private Period convert(String source, @Nullable PeriodStyle style, @Nullable ChronoUnit unit) {
      style = style != null ? style : PeriodStyle.detect(source);
      return style.parse(source, unit);
   }
}
