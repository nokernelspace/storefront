package org.springframework.format.datetime.standard;

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
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

public class Jsr310DateTimeFormatAnnotationFormatterFactory extends EmbeddedValueResolutionSupport implements AnnotationFormatterFactory<DateTimeFormat> {
   private static final Set<Class<?>> FIELD_TYPES = Set.of(
      Instant.class,
      LocalDate.class,
      LocalTime.class,
      LocalDateTime.class,
      ZonedDateTime.class,
      OffsetDateTime.class,
      OffsetTime.class,
      YearMonth.class,
      MonthDay.class
   );

   @Override
   public final Set<Class<?>> getFieldTypes() {
      return FIELD_TYPES;
   }

   public Printer<?> getPrinter(DateTimeFormat annotation, Class<?> fieldType) {
      DateTimeFormatter formatter = this.getFormatter(annotation, fieldType);
      if (formatter == DateTimeFormatter.ISO_DATE) {
         if (this.isLocal(fieldType)) {
            formatter = DateTimeFormatter.ISO_LOCAL_DATE;
         }
      } else if (formatter == DateTimeFormatter.ISO_TIME) {
         if (this.isLocal(fieldType)) {
            formatter = DateTimeFormatter.ISO_LOCAL_TIME;
         }
      } else if (formatter == DateTimeFormatter.ISO_DATE_TIME && this.isLocal(fieldType)) {
         formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
      }

      return new TemporalAccessorPrinter(formatter);
   }

   public Parser<?> getParser(DateTimeFormat annotation, Class<?> fieldType) {
      DateTimeFormatter formatter = this.getFormatter(annotation, fieldType);
      List<String> resolvedFallbackPatterns = new ArrayList<>();

      for (String fallbackPattern : annotation.fallbackPatterns()) {
         String resolvedFallbackPattern = this.resolveEmbeddedValue(fallbackPattern);
         if (StringUtils.hasLength(resolvedFallbackPattern)) {
            resolvedFallbackPatterns.add(resolvedFallbackPattern);
         }
      }

      return new TemporalAccessorParser((Class<? extends TemporalAccessor>)fieldType, formatter, resolvedFallbackPatterns.toArray(new String[0]), annotation);
   }

   protected DateTimeFormatter getFormatter(DateTimeFormat annotation, Class<?> fieldType) {
      DateTimeFormatterFactory factory = new DateTimeFormatterFactory();
      String style = this.resolveEmbeddedValue(annotation.style());
      if (StringUtils.hasLength(style)) {
         factory.setStylePattern(style);
      }

      factory.setIso(annotation.iso());
      String pattern = this.resolveEmbeddedValue(annotation.pattern());
      if (StringUtils.hasLength(pattern)) {
         factory.setPattern(pattern);
      }

      return factory.createDateTimeFormatter();
   }

   private boolean isLocal(Class<?> fieldType) {
      return fieldType.getSimpleName().startsWith("Local");
   }
}
