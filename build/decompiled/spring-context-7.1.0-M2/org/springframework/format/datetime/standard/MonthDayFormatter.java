package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.MonthDay;
import java.util.Locale;
import org.springframework.format.Formatter;

class MonthDayFormatter implements Formatter<MonthDay> {
   public MonthDay parse(String text, Locale locale) throws ParseException {
      return MonthDay.parse(text);
   }

   public String print(MonthDay object, Locale locale) {
      return object.toString();
   }
}
