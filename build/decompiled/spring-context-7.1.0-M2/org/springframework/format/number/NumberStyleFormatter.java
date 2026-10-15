package org.springframework.format.number;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public class NumberStyleFormatter extends AbstractNumberFormatter {
   private @Nullable String pattern;

   public NumberStyleFormatter() {
   }

   public NumberStyleFormatter(String pattern) {
      this.pattern = pattern;
   }

   public void setPattern(String pattern) {
      this.pattern = pattern;
   }

   @Override
   public NumberFormat getNumberFormat(Locale locale) {
      NumberFormat format = NumberFormat.getInstance(locale);
      if (format instanceof DecimalFormat decimalFormat) {
         decimalFormat.setParseBigDecimal(true);
         if (this.pattern != null) {
            decimalFormat.applyPattern(this.pattern);
         }

         return decimalFormat;
      } else if (this.pattern != null) {
         throw new IllegalStateException("Cannot support pattern for non-DecimalFormat: " + format);
      } else {
         return format;
      }
   }
}
