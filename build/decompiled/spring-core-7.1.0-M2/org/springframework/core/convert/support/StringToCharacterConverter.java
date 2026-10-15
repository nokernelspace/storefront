package org.springframework.core.convert.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;

final class StringToCharacterConverter implements Converter<String, Character> {
   public @Nullable Character convert(String source) {
      if (source.isEmpty()) {
         return null;
      } else if (source.length() > 1) {
         throw new IllegalArgumentException(
            "Can only convert a [String] with length of 1 to a [Character]; string value '" + source + "' has length of " + source.length()
         );
      } else {
         return source.charAt(0);
      }
   }
}
