package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.format.Formatter;
import org.springframework.format.annotation.DurationFormat;

public class DurationFormatter implements Formatter<Duration> {
   private final DurationFormat.Style style;
   private final DurationFormat.@Nullable Unit defaultUnit;

   DurationFormatter() {
      this(DurationFormat.Style.ISO8601);
   }

   public DurationFormatter(DurationFormat.Style style) {
      this(style, null);
   }

   public DurationFormatter(DurationFormat.Style style, DurationFormat.@Nullable Unit defaultUnit) {
      this.style = style;
      this.defaultUnit = defaultUnit;
   }

   public Duration parse(String text, Locale locale) throws ParseException {
      return this.defaultUnit == null ? DurationFormatterUtils.parse(text, this.style) : DurationFormatterUtils.parse(text, this.style, this.defaultUnit);
   }

   public String print(Duration object, Locale locale) {
      return this.defaultUnit == null ? DurationFormatterUtils.print(object, this.style) : DurationFormatterUtils.print(object, this.style, this.defaultUnit);
   }
}
