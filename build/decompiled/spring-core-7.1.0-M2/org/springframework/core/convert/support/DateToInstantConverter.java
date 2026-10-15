package org.springframework.core.convert.support;

import java.time.Instant;
import java.util.Date;
import org.springframework.core.convert.converter.Converter;

final class DateToInstantConverter implements Converter<Date, Instant> {
   public Instant convert(Date date) {
      return date.toInstant();
   }
}
