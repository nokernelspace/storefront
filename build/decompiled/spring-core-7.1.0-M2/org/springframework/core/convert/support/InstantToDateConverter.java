package org.springframework.core.convert.support;

import java.time.Instant;
import java.util.Date;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.Converter;

final class InstantToDateConverter implements ConditionalConverter, Converter<Instant, Date> {
   @Override
   public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return targetType.getType().equals(Date.class);
   }

   public Date convert(Instant instant) {
      return Date.from(instant);
   }
}
