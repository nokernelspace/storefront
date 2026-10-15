package org.springframework.boot.convert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.util.ObjectUtils;

final class StringToDurationConverter implements GenericConverter {
   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, Duration.class));
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return ObjectUtils.isEmpty(source) ? null : this.convert(source.toString(), this.getStyle(targetType), this.getDurationUnit(targetType));
   }

   private @Nullable DurationStyle getStyle(TypeDescriptor targetType) {
      DurationFormat annotation = (DurationFormat)targetType.getAnnotation(DurationFormat.class);
      return annotation != null ? annotation.value() : null;
   }

   private @Nullable ChronoUnit getDurationUnit(TypeDescriptor targetType) {
      DurationUnit annotation = (DurationUnit)targetType.getAnnotation(DurationUnit.class);
      return annotation != null ? annotation.value() : null;
   }

   private Duration convert(String source, @Nullable DurationStyle style, @Nullable ChronoUnit unit) {
      style = style != null ? style : DurationStyle.detect(source);
      return style.parse(source, unit);
   }
}
