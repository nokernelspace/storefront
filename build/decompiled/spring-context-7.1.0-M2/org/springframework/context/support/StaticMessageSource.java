package org.springframework.context.support;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class StaticMessageSource extends AbstractMessageSource {
   private final Map<String, Map<Locale, StaticMessageSource.MessageHolder>> messageMap = new HashMap<>();

   @Override
   protected @Nullable String resolveCodeWithoutArguments(String code, Locale locale) {
      Map<Locale, StaticMessageSource.MessageHolder> localeMap = this.messageMap.get(code);
      if (localeMap == null) {
         return null;
      }

      StaticMessageSource.MessageHolder holder = localeMap.get(locale);
      return holder == null ? null : holder.getMessage();
   }

   @Override
   protected @Nullable MessageFormat resolveCode(String code, Locale locale) {
      Map<Locale, StaticMessageSource.MessageHolder> localeMap = this.messageMap.get(code);
      if (localeMap == null) {
         return null;
      }

      StaticMessageSource.MessageHolder holder = localeMap.get(locale);
      return holder == null ? null : holder.getMessageFormat();
   }

   public void addMessage(String code, Locale locale, String msg) {
      Assert.notNull(code, "Code must not be null");
      Assert.notNull(locale, "Locale must not be null");
      Assert.notNull(msg, "Message must not be null");
      this.messageMap.computeIfAbsent(code, key -> new HashMap<>(4)).put(locale, new StaticMessageSource.MessageHolder(msg, locale));
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Added message [" + msg + "] for code [" + code + "] and Locale [" + locale + "]");
      }
   }

   public void addMessages(Map<String, String> messages, Locale locale) {
      Assert.notNull(messages, "Messages Map must not be null");
      messages.forEach((code, msg) -> this.addMessage(code, locale, msg));
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.messageMap;
   }

   private class MessageHolder {
      private final String message;
      private final Locale locale;
      private volatile @Nullable MessageFormat cachedFormat;

      public MessageHolder(String message, Locale locale) {
         this.message = message;
         this.locale = locale;
      }

      public String getMessage() {
         return this.message;
      }

      public MessageFormat getMessageFormat() {
         MessageFormat messageFormat = this.cachedFormat;
         if (messageFormat == null) {
            messageFormat = StaticMessageSource.this.createMessageFormat(this.message, this.locale);
            this.cachedFormat = messageFormat;
         }

         return messageFormat;
      }

      @Override
      public String toString() {
         return this.message;
      }
   }
}
