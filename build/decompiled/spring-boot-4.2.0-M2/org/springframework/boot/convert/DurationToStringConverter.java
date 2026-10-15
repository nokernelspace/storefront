package org.springframework.boot.convert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;

final class DurationToStringConverter implements GenericConverter {
   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(Duration.class, String.class));
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return source == null ? null : this.convert((Duration)source, this.getDurationStyle(sourceType), this.getDurationUnit(sourceType));
   }

   private @Nullable ChronoUnit getDurationUnit(TypeDescriptor sourceType) {
      DurationUnit annotation = (DurationUnit)sourceType.getAnnotation(DurationUnit.class);
      return annotation != null ? annotation.value() : null;
   }

   private @Nullable DurationStyle getDurationStyle(TypeDescriptor sourceType) {
      DurationFormat annotation = (DurationFormat)sourceType.getAnnotation(DurationFormat.class);
      return annotation != null ? annotation.value() : null;
   }

   private String convert(Duration source, @Nullable DurationStyle style, @Nullable ChronoUnit unit) {
      style = style != null ? style : DurationStyle.ISO8601;
      return style.print(source, unit);
   }
}
