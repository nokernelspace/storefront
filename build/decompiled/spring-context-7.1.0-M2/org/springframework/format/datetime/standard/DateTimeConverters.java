package org.springframework.format.datetime.standard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.format.datetime.DateFormatterRegistrar;

final class DateTimeConverters {
   private DateTimeConverters() {
   }

   public static void registerConverters(ConverterRegistry registry) {
      DateFormatterRegistrar.addDateConverters(registry);
      registry.addConverter(new DateTimeConverters.LocalDateTimeToLocalDateConverter());
      registry.addConverter(new DateTimeConverters.LocalDateTimeToLocalTimeConverter());
      registry.addConverter(new DateTimeConverters.ZonedDateTimeToLocalDateConverter());
      registry.addConverter(new DateTimeConverters.ZonedDateTimeToLocalTimeConverter());
      registry.addConverter(new DateTimeConverters.ZonedDateTimeToLocalDateTimeConverter());
      registry.addConverter(new DateTimeConverters.ZonedDateTimeToOffsetDateTimeConverter());
      registry.addConverter(new DateTimeConverters.ZonedDateTimeToInstantConverter());
      registry.addConverter(new DateTimeConverters.OffsetDateTimeToLocalDateConverter());
      registry.addConverter(new DateTimeConverters.OffsetDateTimeToLocalTimeConverter());
      registry.addConverter(new DateTimeConverters.OffsetDateTimeToLocalDateTimeConverter());
      registry.addConverter(new DateTimeConverters.OffsetDateTimeToZonedDateTimeConverter());
      registry.addConverter(new DateTimeConverters.OffsetDateTimeToInstantConverter());
      registry.addConverter(new DateTimeConverters.CalendarToZonedDateTimeConverter());
      registry.addConverter(new DateTimeConverters.CalendarToOffsetDateTimeConverter());
      registry.addConverter(new DateTimeConverters.CalendarToLocalDateConverter());
      registry.addConverter(new DateTimeConverters.CalendarToLocalTimeConverter());
      registry.addConverter(new DateTimeConverters.CalendarToLocalDateTimeConverter());
      registry.addConverter(new DateTimeConverters.CalendarToInstantConverter());
      registry.addConverter(new DateTimeConverters.LongToInstantConverter());
      registry.addConverter(new DateTimeConverters.InstantToLongConverter());
   }

   private static ZonedDateTime calendarToZonedDateTime(Calendar source) {
      return source instanceof GregorianCalendar gc
         ? gc.toZonedDateTime()
         : Instant.ofEpochMilli(source.getTimeInMillis()).atZone(source.getTimeZone().toZoneId());
   }

   private static class CalendarToInstantConverter implements Converter<Calendar, Instant> {
      public Instant convert(Calendar source) {
         return DateTimeConverters.calendarToZonedDateTime(source).toInstant();
      }
   }

   private static class CalendarToLocalDateConverter implements Converter<Calendar, LocalDate> {
      public LocalDate convert(Calendar source) {
         return DateTimeConverters.calendarToZonedDateTime(source).toLocalDate();
      }
   }

   private static class CalendarToLocalDateTimeConverter implements Converter<Calendar, LocalDateTime> {
      public LocalDateTime convert(Calendar source) {
         return DateTimeConverters.calendarToZonedDateTime(source).toLocalDateTime();
      }
   }

   private static class CalendarToLocalTimeConverter implements Converter<Calendar, LocalTime> {
      public LocalTime convert(Calendar source) {
         return DateTimeConverters.calendarToZonedDateTime(source).toLocalTime();
      }
   }

   private static class CalendarToOffsetDateTimeConverter implements Converter<Calendar, OffsetDateTime> {
      public OffsetDateTime convert(Calendar source) {
         return DateTimeConverters.calendarToZonedDateTime(source).toOffsetDateTime();
      }
   }

   private static class CalendarToZonedDateTimeConverter implements Converter<Calendar, ZonedDateTime> {
      public ZonedDateTime convert(Calendar source) {
         return DateTimeConverters.calendarToZonedDateTime(source);
      }
   }

   private static class InstantToLongConverter implements Converter<Instant, Long> {
      public Long convert(Instant source) {
         return source.toEpochMilli();
      }
   }

   private static class LocalDateTimeToLocalDateConverter implements Converter<LocalDateTime, LocalDate> {
      public LocalDate convert(LocalDateTime source) {
         return source.toLocalDate();
      }
   }

   private static class LocalDateTimeToLocalTimeConverter implements Converter<LocalDateTime, LocalTime> {
      public LocalTime convert(LocalDateTime source) {
         return source.toLocalTime();
      }
   }

   private static class LongToInstantConverter implements Converter<Long, Instant> {
      public Instant convert(Long source) {
         return Instant.ofEpochMilli(source);
      }
   }

   private static class OffsetDateTimeToInstantConverter implements Converter<OffsetDateTime, Instant> {
      public Instant convert(OffsetDateTime source) {
         return source.toInstant();
      }
   }

   private static class OffsetDateTimeToLocalDateConverter implements Converter<OffsetDateTime, LocalDate> {
      public LocalDate convert(OffsetDateTime source) {
         return source.toLocalDate();
      }
   }

   private static class OffsetDateTimeToLocalDateTimeConverter implements Converter<OffsetDateTime, LocalDateTime> {
      public LocalDateTime convert(OffsetDateTime source) {
         return source.toLocalDateTime();
      }
   }

   private static class OffsetDateTimeToLocalTimeConverter implements Converter<OffsetDateTime, LocalTime> {
      public LocalTime convert(OffsetDateTime source) {
         return source.toLocalTime();
      }
   }

   private static class OffsetDateTimeToZonedDateTimeConverter implements Converter<OffsetDateTime, ZonedDateTime> {
      public ZonedDateTime convert(OffsetDateTime source) {
         return source.toZonedDateTime();
      }
   }

   private static class ZonedDateTimeToInstantConverter implements Converter<ZonedDateTime, Instant> {
      public Instant convert(ZonedDateTime source) {
         return source.toInstant();
      }
   }

   private static class ZonedDateTimeToLocalDateConverter implements Converter<ZonedDateTime, LocalDate> {
      public LocalDate convert(ZonedDateTime source) {
         return source.toLocalDate();
      }
   }

   private static class ZonedDateTimeToLocalDateTimeConverter implements Converter<ZonedDateTime, LocalDateTime> {
      public LocalDateTime convert(ZonedDateTime source) {
         return source.toLocalDateTime();
      }
   }

   private static class ZonedDateTimeToLocalTimeConverter implements Converter<ZonedDateTime, LocalTime> {
      public LocalTime convert(ZonedDateTime source) {
         return source.toLocalTime();
      }
   }

   private static class ZonedDateTimeToOffsetDateTimeConverter implements Converter<ZonedDateTime, OffsetDateTime> {
      public OffsetDateTime convert(ZonedDateTime source) {
         return source.toOffsetDateTime();
      }
   }
}
