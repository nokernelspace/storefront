package org.springframework.core.convert.support;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

final class StringToLocaleConverter implements Converter<String, Locale> {
   public @Nullable Locale convert(String source) {
      return StringUtils.parseLocale(source);
   }
}
