package org.springframework.context.support;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class MessageSourceSupport {
   static final Set<Locale> JVM_LOCALES = Arrays.stream(Locale.getAvailableLocales())
      .filter(l -> StringUtils.hasLength(l.getLanguage()))
      .collect(Collectors.toSet());
   private static final MessageFormat INVALID_MESSAGE_FORMAT = new MessageFormat("");
   protected final Log logger = LogFactory.getLog(this.getClass());
   private boolean alwaysUseMessageFormat = false;
   private final Map<String, Map<Locale, MessageFormat>> messageFormatsPerMessage = new ConcurrentHashMap<>();

   public void setAlwaysUseMessageFormat(boolean alwaysUseMessageFormat) {
      this.alwaysUseMessageFormat = alwaysUseMessageFormat;
   }

   protected boolean isAlwaysUseMessageFormat() {
      return this.alwaysUseMessageFormat;
   }

   protected String renderDefaultMessage(String defaultMessage, Object @Nullable [] args, @Nullable Locale locale) {
      return this.formatMessage(defaultMessage, args, locale);
   }

   protected String formatMessage(String msg, Object @Nullable [] args, @Nullable Locale locale) {
      if (!this.isAlwaysUseMessageFormat() && ObjectUtils.isEmpty(args)) {
         return msg;
      }

      MessageFormat messageFormat;
      if (locale != null && JVM_LOCALES.contains(locale)) {
         Map<Locale, MessageFormat> messageFormatsPerLocale = this.messageFormatsPerMessage.computeIfAbsent(msg, key -> new ConcurrentHashMap<>());
         messageFormat = messageFormatsPerLocale.computeIfAbsent(locale, key -> this.resolveMessageFormat(msg, key));
      } else {
         messageFormat = this.resolveMessageFormat(msg, locale);
      }

      if (messageFormat == INVALID_MESSAGE_FORMAT) {
         return msg;
      }

      synchronized (messageFormat) {
         return messageFormat.format(this.resolveArguments(args, locale));
      }
   }

   private MessageFormat resolveMessageFormat(String msg, @Nullable Locale locale) {
      try {
         return this.createMessageFormat(msg, locale);
      } catch (IllegalArgumentException ex) {
         if (this.isAlwaysUseMessageFormat()) {
            throw ex;
         } else {
            return INVALID_MESSAGE_FORMAT;
         }
      }
   }

   protected MessageFormat createMessageFormat(String msg, @Nullable Locale locale) {
      return new MessageFormat(msg, locale);
   }

   protected Object[] resolveArguments(Object @Nullable [] args, @Nullable Locale locale) {
      return args != null ? args : new Object[0];
   }
}
