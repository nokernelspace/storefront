package org.springframework.core.convert.support;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;

final class StringToPatternConverter implements Converter<String, Pattern> {
   public @Nullable Pattern convert(String source) {
      return source.isEmpty() ? null : Pattern.compile(source);
   }
}
