package org.springframework.format.datetime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

public class DateTimeFormatAnnotationFormatterFactory extends EmbeddedValueResolutionSupport implements AnnotationFormatterFactory<DateTimeFormat> {
   private static final Set<Class<?>> FIELD_TYPES = Set.of(Date.class, Calendar.class, Long.class);

   @Override
   public Set<Class<?>> getFieldTypes() {
      return FIELD_TYPES;
   }

   public Printer<?> getPrinter(DateTimeFormat annotation, Class<?> fieldType) {
      return this.getFormatter(annotation, fieldType);
   }

   public Parser<?> getParser(DateTimeFormat annotation, Class<?> fieldType) {
      return this.getFormatter(annotation, fieldType);
   }

   protected Formatter<Date> getFormatter(DateTimeFormat annotation, Class<?> fieldType) {
      DateFormatter formatter = new DateFormatter();
      formatter.setSource(annotation);
      formatter.setIso(annotation.iso());
      String style = this.resolveEmbeddedValue(annotation.style());
      if (StringUtils.hasLength(style)) {
         formatter.setStylePattern(style);
      }

      String pattern = this.resolveEmbeddedValue(annotation.pattern());
      if (StringUtils.hasLength(pattern)) {
         formatter.setPattern(pattern);
      }

      List<String> resolvedFallbackPatterns = new ArrayList<>();

      for (String fallbackPattern : annotation.fallbackPatterns()) {
         String resolvedFallbackPattern = this.resolveEmbeddedValue(fallbackPattern);
         if (StringUtils.hasLength(resolvedFallbackPattern)) {
            resolvedFallbackPatterns.add(resolvedFallbackPattern);
         }
      }

      if (!resolvedFallbackPatterns.isEmpty()) {
         formatter.setFallbackPatterns(resolvedFallbackPatterns.toArray(new String[0]));
      }

      return formatter;
   }
}
