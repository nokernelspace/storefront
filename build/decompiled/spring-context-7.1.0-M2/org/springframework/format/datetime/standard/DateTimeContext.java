package org.springframework.format.datetime.standard;

import java.time.ZoneId;
import java.time.chrono.Chronology;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;

public class DateTimeContext {
   private @Nullable Chronology chronology;
   private @Nullable ZoneId timeZone;

   public void setChronology(@Nullable Chronology chronology) {
      this.chronology = chronology;
   }

   public @Nullable Chronology getChronology() {
      return this.chronology;
   }

   public void setTimeZone(@Nullable ZoneId timeZone) {
      this.timeZone = timeZone;
   }

   public @Nullable ZoneId getTimeZone() {
      return this.timeZone;
   }

   public DateTimeFormatter getFormatter(DateTimeFormatter formatter) {
      if (this.chronology != null) {
         formatter = formatter.withChronology(this.chronology);
      }

      if (this.timeZone != null) {
         formatter = formatter.withZone(this.timeZone);
      } else if (LocaleContextHolder.getLocaleContext() instanceof TimeZoneAwareLocaleContext timeZoneAware) {
         TimeZone timeZone = timeZoneAware.getTimeZone();
         if (timeZone != null) {
            formatter = formatter.withZone(timeZone.toZoneId());
         }
      }

      return formatter;
   }
}
