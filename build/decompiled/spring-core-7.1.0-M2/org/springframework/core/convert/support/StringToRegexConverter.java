package org.springframework.core.convert.support;

import kotlin.text.Regex;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;

final class StringToRegexConverter implements Converter<String, Regex> {
   public @Nullable Regex convert(String source) {
      return source.isEmpty() ? null : new Regex(source);
   }
}
