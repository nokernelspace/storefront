package org.springframework.format.datetime;

import java.util.Calendar;
import java.util.Date;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.FormatterRegistry;
import org.springframework.util.Assert;

public class DateFormatterRegistrar implements FormatterRegistrar {
   private @Nullable DateFormatter dateFormatter;

   public void setFormatter(DateFormatter dateFormatter) {
      Assert.notNull(dateFormatter, "DateFormatter must not be null");
      this.dateFormatter = dateFormatter;
   }

   @Override
   public void registerFormatters(FormatterRegistry registry) {
      addDateConverters(registry);
      if (this.dateFormatter != null) {
         registry.addFormatter(this.dateFormatter);
         registry.addFormatterForFieldType(Calendar.class, this.dateFormatter);
      }

      registry.addFormatterForFieldAnnotation(new DateTimeFormatAnnotationFormatterFactory());
   }

   public static void addDateConverters(ConverterRegistry converterRegistry) {
      converterRegistry.addConverter(new DateFormatterRegistrar.DateToLongConverter());
      converterRegistry.addConverter(new DateFormatterRegistrar.DateToCalendarConverter());
      converterRegistry.addConverter(new DateFormatterRegistrar.CalendarToDateConverter());
      converterRegistry.addConverter(new DateFormatterRegistrar.CalendarToLongConverter());
      converterRegistry.addConverter(new DateFormatterRegistrar.LongToDateConverter());
      converterRegistry.addConverter(new DateFormatterRegistrar.LongToCalendarConverter());
   }

   private static class CalendarToDateConverter implements Converter<Calendar, Date> {
      public Date convert(Calendar source) {
         return source.getTime();
      }
   }

   private static class CalendarToLongConverter implements Converter<Calendar, Long> {
      public Long convert(Calendar source) {
         return source.getTimeInMillis();
      }
   }

   private static class DateToCalendarConverter implements Converter<Date, Calendar> {
      public Calendar convert(Date source) {
         Calendar calendar = Calendar.getInstance();
         calendar.setTime(source);
         return calendar;
      }
   }

   private static class DateToLongConverter implements Converter<Date, Long> {
      public Long convert(Date source) {
         return source.getTime();
      }
   }

   private static class LongToCalendarConverter implements Converter<Long, Calendar> {
      public Calendar convert(Long source) {
         Calendar calendar = Calendar.getInstance();
         calendar.setTimeInMillis(source);
         return calendar;
      }
   }

   private static class LongToDateConverter implements Converter<Long, Date> {
      public Date convert(Long source) {
         return new Date(source);
      }
   }
}
