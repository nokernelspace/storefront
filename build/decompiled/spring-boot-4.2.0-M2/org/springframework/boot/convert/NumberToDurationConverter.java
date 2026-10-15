package org.springframework.boot.convert;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;

final class NumberToDurationConverter implements GenericConverter {
   private final StringToDurationConverter delegate = new StringToDurationConverter();

   public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(Number.class, Duration.class));
   }

   public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      return this.delegate.convert(source != null ? source.toString() : null, TypeDescriptor.valueOf(String.class), targetType);
   }
}
