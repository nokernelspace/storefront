package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.Year;
import java.util.Locale;
import org.springframework.format.Formatter;

class YearFormatter implements Formatter<Year> {
   public Year parse(String text, Locale locale) throws ParseException {
      return Year.parse(text);
   }

   public String print(Year object, Locale locale) {
      return object.toString();
   }
}
