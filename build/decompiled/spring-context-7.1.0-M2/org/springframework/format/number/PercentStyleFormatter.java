package org.springframework.format.number;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class PercentStyleFormatter extends AbstractNumberFormatter {
   @Override
   protected NumberFormat getNumberFormat(Locale locale) {
      NumberFormat format = NumberFormat.getPercentInstance(locale);
      if (format instanceof DecimalFormat decimalFormat) {
         decimalFormat.setParseBigDecimal(true);
      }

      return format;
   }
}
