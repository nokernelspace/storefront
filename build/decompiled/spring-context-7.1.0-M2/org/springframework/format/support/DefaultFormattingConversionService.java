package org.springframework.format.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.DateTimeFormatAnnotationFormatterFactory;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.number.NumberFormatAnnotationFormatterFactory;
import org.springframework.format.number.money.CurrencyUnitFormatter;
import org.springframework.format.number.money.Jsr354NumberFormatAnnotationFormatterFactory;
import org.springframework.format.number.money.MonetaryAmountFormatter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringValueResolver;

public class DefaultFormattingConversionService extends FormattingConversionService {
   private static final boolean JSR_354_PRESENT;

   public DefaultFormattingConversionService() {
      this(null, true);
   }

   public DefaultFormattingConversionService(boolean registerDefaultFormatters) {
      this(null, registerDefaultFormatters);
   }

   public DefaultFormattingConversionService(@Nullable StringValueResolver embeddedValueResolver, boolean registerDefaultFormatters) {
      if (embeddedValueResolver != null) {
         this.setEmbeddedValueResolver(embeddedValueResolver);
      }

      DefaultConversionService.addDefaultConverters(this);
      if (registerDefaultFormatters) {
         addDefaultFormatters(this);
      }
   }

   public static void addDefaultFormatters(FormatterRegistry formatterRegistry) {
      formatterRegistry.addFormatterForFieldAnnotation(new NumberFormatAnnotationFormatterFactory());
      if (JSR_354_PRESENT) {
         formatterRegistry.addFormatter(new CurrencyUnitFormatter());
         formatterRegistry.addFormatter(new MonetaryAmountFormatter());
         formatterRegistry.addFormatterForFieldAnnotation(new Jsr354NumberFormatAnnotationFormatterFactory());
      }

      new DateTimeFormatterRegistrar().registerFormatters(formatterRegistry);
      formatterRegistry.addFormatterForFieldAnnotation(new DateTimeFormatAnnotationFormatterFactory());
   }

   static {
      ClassLoader classLoader = DefaultFormattingConversionService.class.getClassLoader();
      JSR_354_PRESENT = ClassUtils.isPresent("javax.money.MonetaryAmount", classLoader);
   }
}
