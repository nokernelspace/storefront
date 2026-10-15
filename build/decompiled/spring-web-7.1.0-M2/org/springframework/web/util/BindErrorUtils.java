package org.springframework.web.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;

public abstract class BindErrorUtils {
   private static final MessageSource defaultMessageSource = new BindErrorUtils.MethodArgumentErrorMessageSource();

   public static String resolveAndJoin(List<? extends MessageSourceResolvable> errors) {
      return resolveAndJoin(errors, defaultMessageSource, Locale.getDefault());
   }

   public static String resolveAndJoin(List<? extends MessageSourceResolvable> errors, MessageSource messageSource, Locale locale) {
      return resolveAndJoin(", and ", "", "", errors, messageSource, locale);
   }

   public static String resolveAndJoin(
      CharSequence delimiter,
      CharSequence prefix,
      CharSequence suffix,
      List<? extends MessageSourceResolvable> errors,
      MessageSource messageSource,
      Locale locale
   ) {
      return errors.stream()
         .map(error -> messageSource.getMessage(error, locale))
         .filter(StringUtils::hasText)
         .collect(Collectors.joining(delimiter, prefix, suffix));
   }

   public static <E extends MessageSourceResolvable> Map<E, String> resolve(List<E> errors) {
      return resolve(errors, defaultMessageSource, Locale.getDefault());
   }

   public static <E extends MessageSourceResolvable> Map<E, String> resolve(List<E> errors, MessageSource messageSource, Locale locale) {
      Map<E, String> map = new LinkedHashMap<>(errors.size());
      errors.forEach(error -> map.put((E)error, messageSource.getMessage(error, locale)));
      return map;
   }

   private static class MethodArgumentErrorMessageSource extends StaticMessageSource {
      MethodArgumentErrorMessageSource() {
         this.setUseCodeAsDefaultMessage(true);
      }

      protected @Nullable String getDefaultMessage(MessageSourceResolvable resolvable, @Nullable Locale locale) {
         String message = super.getDefaultMessage(resolvable, locale);
         return resolvable instanceof FieldError error ? error.getField() + ": " + message : message;
      }
   }
}
