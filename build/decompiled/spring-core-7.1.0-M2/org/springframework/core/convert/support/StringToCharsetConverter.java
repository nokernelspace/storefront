package org.springframework.core.convert.support;

import java.nio.charset.Charset;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

class StringToCharsetConverter implements Converter<String, Charset> {
   public Charset convert(String source) {
      if (StringUtils.hasText(source)) {
         source = source.trim();
      }

      return Charset.forName(source);
   }
}
