package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.format.Formatter;

public class InstantFormatter implements Formatter<Instant> {
   public Instant parse(String text, Locale locale) throws ParseException {
      try {
         return Instant.ofEpochMilli(Long.parseLong(text));
      } catch (NumberFormatException ex) {
         return !text.isEmpty() && Character.isAlphabetic(text.charAt(0))
            ? Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text))
            : Instant.parse(text);
      }
   }

   public String print(Instant object, Locale locale) {
      return object.toString();
   }
}
