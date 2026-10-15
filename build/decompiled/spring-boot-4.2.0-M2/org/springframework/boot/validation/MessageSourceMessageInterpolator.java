package org.springframework.boot.validation;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.MessageInterpolator.Context;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

class MessageSourceMessageInterpolator implements MessageInterpolator {
   private static final String DEFAULT_MESSAGE = MessageSourceMessageInterpolator.class.getName();
   private static final char PREFIX = '{';
   private static final char SUFFIX = '}';
   private static final char ESCAPE = '\\';
   private final MessageSource messageSource;
   private final MessageInterpolator messageInterpolator;

   MessageSourceMessageInterpolator(MessageSource messageSource, MessageInterpolator messageInterpolator) {
      this.messageSource = messageSource;
      this.messageInterpolator = messageInterpolator;
   }

   public String interpolate(String messageTemplate, Context context) {
      return this.interpolate(messageTemplate, context, LocaleContextHolder.getLocale());
   }

   public String interpolate(String messageTemplate, Context context, Locale locale) {
      String message = this.replaceParameters(messageTemplate, locale);
      return this.messageInterpolator.interpolate(message, context, locale);
   }

   private String replaceParameters(String message, Locale locale) {
      return this.replaceParameters(message, locale, new LinkedHashSet<>(4));
   }

   private String replaceParameters(String message, Locale locale, Set<String> visitedParameters) {
      StringBuilder buf = new StringBuilder(message);
      int parentheses = 0;
      int startIndex = -1;
      int endIndex = -1;

      for (int i = 0; i < buf.length(); i++) {
         if (buf.charAt(i) == '\\') {
            i++;
         } else if (buf.charAt(i) == '{') {
            if (startIndex == -1) {
               startIndex = i;
            }

            parentheses++;
         } else if (buf.charAt(i) == '}') {
            if (parentheses > 0) {
               parentheses--;
            }

            endIndex = i;
         }

         if (parentheses == 0 && startIndex < endIndex) {
            String parameter = buf.substring(startIndex + 1, endIndex);
            if (!visitedParameters.add(parameter)) {
               throw new IllegalArgumentException("Circular reference '{" + String.join(" -> ", visitedParameters) + " -> " + parameter + "}'");
            }

            String value = this.replaceParameter(parameter, locale, visitedParameters);
            if (value != null) {
               buf.replace(startIndex, endIndex + 1, value);
               i = startIndex + value.length() - 1;
            }

            visitedParameters.remove(parameter);
            startIndex = -1;
            endIndex = -1;
         }
      }

      return buf.toString();
   }

   private @Nullable String replaceParameter(String parameter, Locale locale, Set<String> visitedParameters) {
      parameter = this.replaceParameters(parameter, locale, visitedParameters);
      String value = this.messageSource.getMessage(parameter, null, DEFAULT_MESSAGE, locale);
      return value != null && !value.equals(DEFAULT_MESSAGE) ? this.replaceParameters(value, locale, visitedParameters) : null;
   }
}
