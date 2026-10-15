package org.springframework.core.convert.support;

import org.springframework.core.convert.converter.Converter;
import org.springframework.util.unit.DataSize;

final class NumberToDataSizeConverter implements Converter<Number, DataSize> {
   public DataSize convert(Number source) {
      long bytes = source.longValue();
      if (source.doubleValue() - bytes != 0.0) {
         throw new IllegalArgumentException("'" + source + "' is not a valid data size");
      } else {
         return DataSize.ofBytes(bytes);
      }
   }
}
