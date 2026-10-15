package org.springframework.format.datetime.standard;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import org.springframework.format.Printer;

public final class TemporalAccessorPrinter implements Printer<TemporalAccessor> {
   private final DateTimeFormatter formatter;

   public TemporalAccessorPrinter(DateTimeFormatter formatter) {
      this.formatter = formatter;
   }

   public String print(TemporalAccessor partial, Locale locale) {
      return DateTimeContextHolder.getFormatter(this.formatter, locale).format(partial);
   }
}
