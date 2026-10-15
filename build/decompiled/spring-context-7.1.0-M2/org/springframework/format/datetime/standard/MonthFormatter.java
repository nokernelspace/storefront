package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.Month;
import java.util.Locale;
import org.springframework.format.Formatter;

class MonthFormatter implements Formatter<Month> {
   public Month parse(String text, Locale locale) throws ParseException {
      return Month.valueOf(text.toUpperCase(Locale.ROOT));
   }

   public String print(Month object, Locale locale) {
      return object.toString();
   }
}
