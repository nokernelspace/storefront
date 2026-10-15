package org.springframework.format.number;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Currency;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public class CurrencyStyleFormatter extends AbstractNumberFormatter {
   private int fractionDigits = 2;
   private @Nullable RoundingMode roundingMode;
   private @Nullable Currency currency;
   private @Nullable String pattern;

   public void setFractionDigits(int fractionDigits) {
      this.fractionDigits = fractionDigits;
   }

   public void setRoundingMode(RoundingMode roundingMode) {
      this.roundingMode = roundingMode;
   }

   public void setCurrency(Currency currency) {
      this.currency = currency;
   }

   public void setPattern(String pattern) {
      this.pattern = pattern;
   }

   public BigDecimal parse(String text, Locale locale) throws ParseException {
      BigDecimal decimal = (BigDecimal)super.parse(text, locale);
      if (this.roundingMode != null) {
         decimal = decimal.setScale(this.fractionDigits, this.roundingMode);
      } else {
         decimal = decimal.setScale(this.fractionDigits);
      }

      return decimal;
   }

   @Override
   protected NumberFormat getNumberFormat(Locale locale) {
      DecimalFormat format = (DecimalFormat)NumberFormat.getCurrencyInstance(locale);
      format.setParseBigDecimal(true);
      format.setMaximumFractionDigits(this.fractionDigits);
      format.setMinimumFractionDigits(this.fractionDigits);
      if (this.roundingMode != null) {
         format.setRoundingMode(this.roundingMode);
      }

      if (this.currency != null) {
         format.setCurrency(this.currency);
      }

      if (this.pattern != null) {
         format.applyPattern(this.pattern);
      }

      return format;
   }
}
