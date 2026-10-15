package org.springframework.validation.beanvalidation;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.MessageInterpolator.Context;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.Assert;

public class LocaleContextMessageInterpolator implements MessageInterpolator {
   private final MessageInterpolator targetInterpolator;

   public LocaleContextMessageInterpolator(MessageInterpolator targetInterpolator) {
      Assert.notNull(targetInterpolator, "Target MessageInterpolator must not be null");
      this.targetInterpolator = targetInterpolator;
   }

   public String interpolate(String message, Context context) {
      return this.targetInterpolator.interpolate(message, context, LocaleContextHolder.getLocale());
   }

   public String interpolate(String message, Context context, Locale locale) {
      return this.targetInterpolator.interpolate(message, context, locale);
   }
}
