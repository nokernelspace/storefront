package org.springframework.context.support;

import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.util.Assert;

public class MessageSourceResourceBundle extends ResourceBundle {
   private final MessageSource messageSource;
   private final Locale locale;

   public MessageSourceResourceBundle(MessageSource source, Locale locale) {
      Assert.notNull(source, "MessageSource must not be null");
      this.messageSource = source;
      this.locale = locale;
   }

   public MessageSourceResourceBundle(MessageSource source, Locale locale, ResourceBundle parent) {
      this(source, locale);
      this.setParent(parent);
   }

   @Override
   protected @Nullable Object handleGetObject(String key) {
      try {
         return this.messageSource.getMessage(key, null, this.locale);
      } catch (NoSuchMessageException ex) {
         return null;
      }
   }

   @Override
   public boolean containsKey(String key) {
      try {
         this.messageSource.getMessage(key, null, this.locale);
         return true;
      } catch (NoSuchMessageException ex) {
         return false;
      }
   }

   @Override
   public Enumeration<String> getKeys() {
      throw new UnsupportedOperationException("MessageSourceResourceBundle does not support enumerating its keys");
   }

   @Override
   public Locale getLocale() {
      return this.locale;
   }
}
