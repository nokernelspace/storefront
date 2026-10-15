package org.springframework.context.support;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;

public class DelegatingMessageSource extends MessageSourceSupport implements HierarchicalMessageSource {
   private @Nullable MessageSource parentMessageSource;

   @Override
   public void setParentMessageSource(@Nullable MessageSource parent) {
      this.parentMessageSource = parent;
   }

   @Override
   public @Nullable MessageSource getParentMessageSource() {
      return this.parentMessageSource;
   }

   @Override
   public @Nullable String getMessage(String code, Object @Nullable [] args, @Nullable String defaultMessage, @Nullable Locale locale) {
      if (this.parentMessageSource != null) {
         return this.parentMessageSource.getMessage(code, args, defaultMessage, locale);
      } else {
         return defaultMessage != null ? this.renderDefaultMessage(defaultMessage, args, locale) : null;
      }
   }

   @Override
   public String getMessage(String code, Object @Nullable [] args, @Nullable Locale locale) throws NoSuchMessageException {
      if (this.parentMessageSource != null) {
         return this.parentMessageSource.getMessage(code, args, locale);
      } else if (locale == null) {
         throw new NoSuchMessageException(code);
      } else {
         throw new NoSuchMessageException(code, locale);
      }
   }

   @Override
   public String getMessage(MessageSourceResolvable resolvable, @Nullable Locale locale) throws NoSuchMessageException {
      if (this.parentMessageSource != null) {
         return this.parentMessageSource.getMessage(resolvable, locale);
      } else if (resolvable.getDefaultMessage() != null) {
         return this.renderDefaultMessage(resolvable.getDefaultMessage(), resolvable.getArguments(), locale);
      } else {
         String[] codes = resolvable.getCodes();
         String code = codes != null && codes.length > 0 ? codes[0] : "";
         if (locale == null) {
            throw new NoSuchMessageException(code);
         } else {
            throw new NoSuchMessageException(code, locale);
         }
      }
   }

   @Override
   public String toString() {
      return this.parentMessageSource != null ? this.parentMessageSource.toString() : "Empty MessageSource";
   }
}
