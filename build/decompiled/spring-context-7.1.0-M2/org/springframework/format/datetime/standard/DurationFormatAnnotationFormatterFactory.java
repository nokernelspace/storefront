package org.springframework.format.datetime.standard;

import java.time.Duration;
import java.util.Set;
import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.DurationFormat;

public class DurationFormatAnnotationFormatterFactory extends EmbeddedValueResolutionSupport implements AnnotationFormatterFactory<DurationFormat> {
   private static final Set<Class<?>> FIELD_TYPES = Set.of(Duration.class);

   @Override
   public final Set<Class<?>> getFieldTypes() {
      return FIELD_TYPES;
   }

   public Printer<?> getPrinter(DurationFormat annotation, Class<?> fieldType) {
      return new DurationFormatter(annotation.style(), annotation.defaultUnit());
   }

   public Parser<?> getParser(DurationFormat annotation, Class<?> fieldType) {
      return new DurationFormatter(annotation.style(), annotation.defaultUnit());
   }
}
