package org.springframework.core.convert.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.unit.DataSize;

final class StringToDataSizeConverter implements Converter<String, DataSize> {
   public @Nullable DataSize convert(String source) {
      return source.isEmpty() ? null : DataSize.parse(source);
   }
}
