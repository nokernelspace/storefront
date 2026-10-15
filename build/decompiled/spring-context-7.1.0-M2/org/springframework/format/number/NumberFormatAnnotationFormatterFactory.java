package org.springframework.format.number;

import java.util.Set;
import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;

public class NumberFormatAnnotationFormatterFactory extends EmbeddedValueResolutionSupport implements AnnotationFormatterFactory<NumberFormat> {
   @Override
   public Set<Class<?>> getFieldTypes() {
      return NumberUtils.STANDARD_NUMBER_TYPES;
   }

   public Printer<Number> getPrinter(NumberFormat annotation, Class<?> fieldType) {
      return this.configureFormatterFrom(annotation);
   }

   public Parser<Number> getParser(NumberFormat annotation, Class<?> fieldType) {
      return this.configureFormatterFrom(annotation);
   }

   private Formatter<Number> configureFormatterFrom(NumberFormat annotation) {
      String pattern = this.resolveEmbeddedValue(annotation.pattern());
      if (StringUtils.hasLength(pattern)) {
         return new NumberStyleFormatter(pattern);
      } else {
         NumberFormat.Style style = annotation.style();
         if (style == NumberFormat.Style.CURRENCY) {
            return new CurrencyStyleFormatter();
         } else {
            return style == NumberFormat.Style.PERCENT ? new PercentStyleFormatter() : new NumberStyleFormatter();
         }
      }
   }
}
