package org.springframework.format.number.money;

import java.text.ParseException;
import java.util.Collections;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import org.springframework.context.support.EmbeddedValueResolutionSupport;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.number.CurrencyStyleFormatter;
import org.springframework.format.number.NumberStyleFormatter;
import org.springframework.format.number.PercentStyleFormatter;
import org.springframework.util.StringUtils;

public class Jsr354NumberFormatAnnotationFormatterFactory extends EmbeddedValueResolutionSupport implements AnnotationFormatterFactory<NumberFormat> {
   private static final String CURRENCY_CODE_PATTERN = "¤¤";

   @Override
   public Set<Class<?>> getFieldTypes() {
      return Collections.singleton(MonetaryAmount.class);
   }

   public Printer<MonetaryAmount> getPrinter(NumberFormat annotation, Class<?> fieldType) {
      return this.configureFormatterFrom(annotation);
   }

   public Parser<MonetaryAmount> getParser(NumberFormat annotation, Class<?> fieldType) {
      return this.configureFormatterFrom(annotation);
   }

   private Formatter<MonetaryAmount> configureFormatterFrom(NumberFormat annotation) {
      String pattern = this.resolveEmbeddedValue(annotation.pattern());
      if (StringUtils.hasLength(pattern)) {
         return new Jsr354NumberFormatAnnotationFormatterFactory.PatternDecoratingFormatter(pattern);
      } else {
         NumberFormat.Style style = annotation.style();
         if (style == NumberFormat.Style.NUMBER) {
            return new Jsr354NumberFormatAnnotationFormatterFactory.NumberDecoratingFormatter(new NumberStyleFormatter());
         } else {
            return style == NumberFormat.Style.PERCENT
               ? new Jsr354NumberFormatAnnotationFormatterFactory.NumberDecoratingFormatter(new PercentStyleFormatter())
               : new Jsr354NumberFormatAnnotationFormatterFactory.NumberDecoratingFormatter(new CurrencyStyleFormatter());
         }
      }
   }

   private static class NumberDecoratingFormatter implements Formatter<MonetaryAmount> {
      private final Formatter<Number> numberFormatter;

      public NumberDecoratingFormatter(Formatter<Number> numberFormatter) {
         this.numberFormatter = numberFormatter;
      }

      public String print(MonetaryAmount object, Locale locale) {
         return this.numberFormatter.print(object.getNumber(), locale);
      }

      public MonetaryAmount parse(String text, Locale locale) throws ParseException {
         CurrencyUnit currencyUnit = Monetary.getCurrency(locale, new String[0]);
         Number numberValue = this.numberFormatter.parse(text, locale);
         return Monetary.getDefaultAmountFactory().setNumber(numberValue).setCurrency(currencyUnit).create();
      }
   }

   private static class PatternDecoratingFormatter implements Formatter<MonetaryAmount> {
      private final String pattern;

      public PatternDecoratingFormatter(String pattern) {
         this.pattern = pattern;
      }

      public String print(MonetaryAmount object, Locale locale) {
         CurrencyStyleFormatter formatter = new CurrencyStyleFormatter();
         formatter.setCurrency(Currency.getInstance(object.getCurrency().getCurrencyCode()));
         formatter.setPattern(this.pattern);
         return formatter.print(object.getNumber(), locale);
      }

      public MonetaryAmount parse(String text, Locale locale) throws ParseException {
         CurrencyStyleFormatter formatter = new CurrencyStyleFormatter();
         Currency currency = this.determineCurrency(text, locale);
         CurrencyUnit currencyUnit = Monetary.getCurrency(currency.getCurrencyCode(), new String[0]);
         formatter.setCurrency(currency);
         formatter.setPattern(this.pattern);
         Number numberValue = formatter.parse(text, locale);
         return Monetary.getDefaultAmountFactory().setNumber(numberValue).setCurrency(currencyUnit).create();
      }

      private Currency determineCurrency(String text, Locale locale) {
         try {
            if (text.length() < 3) {
               return Currency.getInstance(locale);
            } else if (this.pattern.startsWith("¤¤")) {
               return Currency.getInstance(text.substring(0, 3));
            } else {
               return this.pattern.endsWith("¤¤") ? Currency.getInstance(text.substring(text.length() - 3)) : Currency.getInstance(locale);
            }
         } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cannot determine currency for number value [" + text + "]", ex);
         }
      }
   }
}
