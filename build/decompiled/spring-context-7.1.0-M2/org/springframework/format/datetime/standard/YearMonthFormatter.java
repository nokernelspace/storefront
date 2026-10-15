package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.format.Formatter;

class YearMonthFormatter implements Formatter<YearMonth> {
   public YearMonth parse(String text, Locale locale) throws ParseException {
      return YearMonth.parse(text);
   }

   public String print(YearMonth object, Locale locale) {
      return object.toString();
   }
}
