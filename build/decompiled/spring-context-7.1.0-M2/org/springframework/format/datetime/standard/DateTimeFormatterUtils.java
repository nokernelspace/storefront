package org.springframework.format.datetime.standard;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import org.springframework.util.StringUtils;

abstract class DateTimeFormatterUtils {
   static DateTimeFormatter createStrictDateTimeFormatter(String pattern) {
      String patternToUse = StringUtils.replace(pattern, "yy", "uu");
      return DateTimeFormatter.ofPattern(patternToUse).withResolverStyle(ResolverStyle.STRICT);
   }
}
