package org.springframework.core.convert.support;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;

final class StringToBooleanConverter implements Converter<String, Boolean> {
   private static final Set<String> trueValues = Set.of("true", "on", "yes", "1");
   private static final Set<String> falseValues = Set.of("false", "off", "no", "0");

   public @Nullable Boolean convert(String source) {
      String value = source.trim();
      if (value.isEmpty()) {
         return null;
      } else {
         value = value.toLowerCase(Locale.ROOT);
         if (trueValues.contains(value)) {
            return Boolean.TRUE;
         } else if (falseValues.contains(value)) {
            return Boolean.FALSE;
         } else {
            throw new IllegalArgumentException("Invalid boolean value '" + source + "'");
         }
      }
   }
}
