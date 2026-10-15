package org.springframework.format.datetime.standard;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.format.Parser;
import org.springframework.util.ObjectUtils;

public final class TemporalAccessorParser implements Parser<TemporalAccessor> {
   private final Class<? extends TemporalAccessor> temporalAccessorType;
   private final DateTimeFormatter formatter;
   private final String @Nullable [] fallbackPatterns;
   private final @Nullable Object source;

   public TemporalAccessorParser(Class<? extends TemporalAccessor> temporalAccessorType, DateTimeFormatter formatter) {
      this(temporalAccessorType, formatter, null, null);
   }

   TemporalAccessorParser(
      Class<? extends TemporalAccessor> temporalAccessorType, DateTimeFormatter formatter, String @Nullable [] fallbackPatterns, @Nullable Object source
   ) {
      this.temporalAccessorType = temporalAccessorType;
      this.formatter = formatter;
      this.fallbackPatterns = fallbackPatterns;
      this.source = source;
   }

   public TemporalAccessor parse(String text, Locale locale) throws ParseException {
      try {
         return this.doParse(text, locale, this.formatter);
      } catch (DateTimeParseException ex) {
         if (ObjectUtils.isEmpty(this.fallbackPatterns)) {
            try {
               return this.defaultParse(text);
            } catch (DateTimeParseException var9) {
            }
         } else {
            for (String pattern : this.fallbackPatterns) {
               try {
                  DateTimeFormatter fallbackFormatter = DateTimeFormatterUtils.createStrictDateTimeFormatter(pattern);
                  return this.doParse(text, locale, fallbackFormatter);
               } catch (DateTimeParseException var10) {
               }
            }
         }

         if (this.source != null) {
            throw new DateTimeParseException(
               String.format("Unable to parse date time value \"%s\" using configuration from %s", text, this.source), text, ex.getErrorIndex(), ex
            );
         } else {
            throw ex;
         }
      }
   }

   private TemporalAccessor doParse(String text, Locale locale, DateTimeFormatter formatter) throws DateTimeParseException {
      DateTimeFormatter formatterToUse = DateTimeContextHolder.getFormatter(formatter, locale);
      if (Instant.class == this.temporalAccessorType) {
         return formatterToUse.parse(text, Instant::from);
      } else if (LocalDate.class == this.temporalAccessorType) {
         return LocalDate.parse(text, formatterToUse);
      } else if (LocalTime.class == this.temporalAccessorType) {
         return LocalTime.parse(text, formatterToUse);
      } else if (LocalDateTime.class == this.temporalAccessorType) {
         return LocalDateTime.parse(text, formatterToUse);
      } else if (ZonedDateTime.class == this.temporalAccessorType) {
         return ZonedDateTime.parse(text, formatterToUse);
      } else if (OffsetDateTime.class == this.temporalAccessorType) {
         return OffsetDateTime.parse(text, formatterToUse);
      } else if (OffsetTime.class == this.temporalAccessorType) {
         return OffsetTime.parse(text, formatterToUse);
      } else if (YearMonth.class == this.temporalAccessorType) {
         return YearMonth.parse(text, formatterToUse);
      } else if (MonthDay.class == this.temporalAccessorType) {
         return MonthDay.parse(text, formatterToUse);
      } else {
         throw new IllegalStateException("Unsupported TemporalAccessor type: " + this.temporalAccessorType);
      }
   }

   private TemporalAccessor defaultParse(String text) throws DateTimeParseException {
      if (Instant.class == this.temporalAccessorType) {
         return Instant.parse(text);
      } else if (LocalDate.class == this.temporalAccessorType) {
         return LocalDate.parse(text);
      } else if (LocalTime.class == this.temporalAccessorType) {
         return LocalTime.parse(text);
      } else if (LocalDateTime.class == this.temporalAccessorType) {
         return LocalDateTime.parse(text);
      } else if (ZonedDateTime.class == this.temporalAccessorType) {
         return ZonedDateTime.parse(text);
      } else if (OffsetDateTime.class == this.temporalAccessorType) {
         return OffsetDateTime.parse(text);
      } else if (OffsetTime.class == this.temporalAccessorType) {
         return OffsetTime.parse(text);
      } else if (YearMonth.class == this.temporalAccessorType) {
         return YearMonth.parse(text);
      } else if (MonthDay.class == this.temporalAccessorType) {
         return MonthDay.parse(text);
      } else {
         throw new IllegalStateException("Unsupported TemporalAccessor type: " + this.temporalAccessorType);
      }
   }
}
