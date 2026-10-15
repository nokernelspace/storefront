package org.springframework.format.datetime.standard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.annotation.DateTimeFormat;

public class DateTimeFormatterRegistrar implements FormatterRegistrar {
   private final Map<DateTimeFormatterRegistrar.Type, DateTimeFormatter> formatters = new EnumMap<>(DateTimeFormatterRegistrar.Type.class);
   private final Map<DateTimeFormatterRegistrar.Type, DateTimeFormatterFactory> factories = new EnumMap<>(DateTimeFormatterRegistrar.Type.class);

   public DateTimeFormatterRegistrar() {
      for (DateTimeFormatterRegistrar.Type type : DateTimeFormatterRegistrar.Type.values()) {
         this.factories.put(type, new DateTimeFormatterFactory());
      }
   }

   public void setUseIsoFormat(boolean useIsoFormat) {
      this.getFactory(DateTimeFormatterRegistrar.Type.DATE).setIso(useIsoFormat ? DateTimeFormat.ISO.DATE : DateTimeFormat.ISO.NONE);
      this.getFactory(DateTimeFormatterRegistrar.Type.TIME).setIso(useIsoFormat ? DateTimeFormat.ISO.TIME : DateTimeFormat.ISO.NONE);
      this.getFactory(DateTimeFormatterRegistrar.Type.DATE_TIME).setIso(useIsoFormat ? DateTimeFormat.ISO.DATE_TIME : DateTimeFormat.ISO.NONE);
   }

   public void setDateStyle(FormatStyle dateStyle) {
      this.getFactory(DateTimeFormatterRegistrar.Type.DATE).setDateStyle(dateStyle);
   }

   public void setTimeStyle(FormatStyle timeStyle) {
      this.getFactory(DateTimeFormatterRegistrar.Type.TIME).setTimeStyle(timeStyle);
   }

   public void setDateTimeStyle(FormatStyle dateTimeStyle) {
      this.getFactory(DateTimeFormatterRegistrar.Type.DATE_TIME).setDateTimeStyle(dateTimeStyle);
   }

   public void setDateFormatter(DateTimeFormatter formatter) {
      this.formatters.put(DateTimeFormatterRegistrar.Type.DATE, formatter);
   }

   public void setTimeFormatter(DateTimeFormatter formatter) {
      this.formatters.put(DateTimeFormatterRegistrar.Type.TIME, formatter);
   }

   public void setDateTimeFormatter(DateTimeFormatter formatter) {
      this.formatters.put(DateTimeFormatterRegistrar.Type.DATE_TIME, formatter);
   }

   @Override
   public void registerFormatters(FormatterRegistry registry) {
      DateTimeConverters.registerConverters(registry);
      DateTimeFormatter df = this.getFormatter(DateTimeFormatterRegistrar.Type.DATE);
      DateTimeFormatter tf = this.getFormatter(DateTimeFormatterRegistrar.Type.TIME);
      DateTimeFormatter dtf = this.getFormatter(DateTimeFormatterRegistrar.Type.DATE_TIME);
      registry.addFormatterForFieldType(
         LocalDate.class,
         new TemporalAccessorPrinter(df == DateTimeFormatter.ISO_DATE ? DateTimeFormatter.ISO_LOCAL_DATE : df),
         new TemporalAccessorParser(LocalDate.class, df)
      );
      registry.addFormatterForFieldType(
         LocalTime.class,
         new TemporalAccessorPrinter(tf == DateTimeFormatter.ISO_TIME ? DateTimeFormatter.ISO_LOCAL_TIME : tf),
         new TemporalAccessorParser(LocalTime.class, tf)
      );
      registry.addFormatterForFieldType(
         LocalDateTime.class,
         new TemporalAccessorPrinter(dtf == DateTimeFormatter.ISO_DATE_TIME ? DateTimeFormatter.ISO_LOCAL_DATE_TIME : dtf),
         new TemporalAccessorParser(LocalDateTime.class, dtf)
      );
      registry.addFormatterForFieldType(ZonedDateTime.class, new TemporalAccessorPrinter(dtf), new TemporalAccessorParser(ZonedDateTime.class, dtf));
      registry.addFormatterForFieldType(OffsetDateTime.class, new TemporalAccessorPrinter(dtf), new TemporalAccessorParser(OffsetDateTime.class, dtf));
      registry.addFormatterForFieldType(OffsetTime.class, new TemporalAccessorPrinter(tf), new TemporalAccessorParser(OffsetTime.class, tf));
      registry.addFormatterForFieldType(Instant.class, new InstantFormatter());
      registry.addFormatterForFieldType(Period.class, new PeriodFormatter());
      registry.addFormatterForFieldType(Duration.class, new DurationFormatter());
      registry.addFormatterForFieldType(Year.class, new YearFormatter());
      registry.addFormatterForFieldType(Month.class, new MonthFormatter());
      registry.addFormatterForFieldType(YearMonth.class, new YearMonthFormatter());
      registry.addFormatterForFieldType(MonthDay.class, new MonthDayFormatter());
      registry.addFormatterForFieldAnnotation(new Jsr310DateTimeFormatAnnotationFormatterFactory());
      registry.addFormatterForFieldAnnotation(new DurationFormatAnnotationFormatterFactory());
   }

   private DateTimeFormatter getFormatter(DateTimeFormatterRegistrar.Type type) {
      DateTimeFormatter formatter = this.formatters.get(type);
      if (formatter != null) {
         return formatter;
      }

      DateTimeFormatter fallbackFormatter = this.getFallbackFormatter(type);
      return this.getFactory(type).createDateTimeFormatter(fallbackFormatter);
   }

   private DateTimeFormatterFactory getFactory(DateTimeFormatterRegistrar.Type type) {
      return this.factories.get(type);
   }

   private DateTimeFormatter getFallbackFormatter(DateTimeFormatterRegistrar.Type type) {
      return switch (type) {
         case DATE -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
         case TIME -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
         case DATE_TIME -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
      };
   }

   private enum Type {
      DATE,
      TIME,
      DATE_TIME;
   }
}
