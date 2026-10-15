package org.springframework.context.support;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.util.ObjectUtils;

public abstract class AbstractMessageSource extends MessageSourceSupport implements HierarchicalMessageSource {
   private @Nullable MessageSource parentMessageSource;
   private @Nullable Properties commonMessages;
   private boolean useCodeAsDefaultMessage = false;

   @Override
   public void setParentMessageSource(@Nullable MessageSource parent) {
      this.parentMessageSource = parent;
   }

   @Override
   public @Nullable MessageSource getParentMessageSource() {
      return this.parentMessageSource;
   }

   public void setCommonMessages(@Nullable Properties commonMessages) {
      this.commonMessages = commonMessages;
   }

   protected @Nullable Properties getCommonMessages() {
      return this.commonMessages;
   }

   public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
      this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
   }

   protected boolean isUseCodeAsDefaultMessage() {
      return this.useCodeAsDefaultMessage;
   }

   @Override
   public final @Nullable String getMessage(String code, Object @Nullable [] args, @Nullable String defaultMessage, @Nullable Locale locale) {
      String msg = this.getMessageInternal(code, args, locale);
      if (msg != null) {
         return msg;
      } else {
         return defaultMessage == null ? this.getDefaultMessage(code) : this.renderDefaultMessage(defaultMessage, args, locale);
      }
   }

   @Override
   public final String getMessage(String code, Object @Nullable [] args, @Nullable Locale locale) throws NoSuchMessageException {
      String msg = this.getMessageInternal(code, args, locale);
      if (msg != null) {
         return msg;
      } else {
         String fallback = this.getDefaultMessage(code);
         if (fallback != null) {
            return fallback;
         } else if (locale == null) {
            throw new NoSuchMessageException(code);
         } else {
            throw new NoSuchMessageException(code, locale);
         }
      }
   }

   @Override
   public final String getMessage(MessageSourceResolvable resolvable, @Nullable Locale locale) throws NoSuchMessageException {
      String[] codes = resolvable.getCodes();
      if (codes != null) {
         for (String code : codes) {
            String message = this.getMessageInternal(code, resolvable.getArguments(), locale);
            if (message != null) {
               return message;
            }
         }
      }

      String defaultMessage = this.getDefaultMessage(resolvable, locale);
      if (defaultMessage != null) {
         return defaultMessage;
      } else {
         String code = !ObjectUtils.isEmpty(codes) ? codes[codes.length - 1] : "";
         if (locale == null) {
            throw new NoSuchMessageException(code);
         } else {
            throw new NoSuchMessageException(code, locale);
         }
      }
   }

   protected @Nullable String getMessageInternal(@Nullable String code, Object @Nullable [] args, @Nullable Locale locale) {
      if (code == null) {
         return null;
      }

      if (locale == null) {
         locale = Locale.getDefault();
      }

      Object[] argsToUse = args;
      if (!this.isAlwaysUseMessageFormat() && ObjectUtils.isEmpty(args)) {
         String message = this.resolveCodeWithoutArguments(code, locale);
         if (message != null) {
            return message;
         }
      } else {
         argsToUse = this.resolveArguments(args, locale);
         MessageFormat messageFormat = this.resolveCode(code, locale);
         if (messageFormat != null) {
            synchronized (messageFormat) {
               return messageFormat.format(argsToUse);
            }
         }
      }

      Properties commonMessages = this.getCommonMessages();
      if (commonMessages != null) {
         String commonMessage = commonMessages.getProperty(code);
         if (commonMessage != null) {
            return this.formatMessage(commonMessage, args, locale);
         }
      }

      return this.getMessageFromParent(code, argsToUse, locale);
   }

   protected @Nullable String getMessageFromParent(String code, Object @Nullable [] args, Locale locale) {
      MessageSource parent = this.getParentMessageSource();
      if (parent != null) {
         return parent instanceof AbstractMessageSource abstractMessageSource
            ? abstractMessageSource.getMessageInternal(code, args, locale)
            : parent.getMessage(code, args, null, locale);
      } else {
         return null;
      }
   }

   protected @Nullable String getDefaultMessage(MessageSourceResolvable resolvable, @Nullable Locale locale) {
      String defaultMessage = resolvable.getDefaultMessage();
      String[] codes = resolvable.getCodes();
      if (defaultMessage != null) {
         if (resolvable instanceof DefaultMessageSourceResolvable defaultMessageSourceResolvable
            && !defaultMessageSourceResolvable.shouldRenderDefaultMessage()) {
            return defaultMessage;
         } else {
            return !ObjectUtils.isEmpty(codes) && defaultMessage.equals(codes[0])
               ? defaultMessage
               : this.renderDefaultMessage(defaultMessage, resolvable.getArguments(), locale);
         }
      } else {
         return !ObjectUtils.isEmpty(codes) ? this.getDefaultMessage(codes[0]) : null;
      }
   }

   protected @Nullable String getDefaultMessage(String code) {
      return this.isUseCodeAsDefaultMessage() ? code : null;
   }

   @Override
   protected Object[] resolveArguments(Object @Nullable [] args, @Nullable Locale locale) {
      if (ObjectUtils.isEmpty(args)) {
         return super.resolveArguments(args, locale);
      }

      List<Object> resolvedArgs = new ArrayList<>(args.length);

      for (Object arg : args) {
         if (arg instanceof MessageSourceResolvable messageSourceResolvable) {
            resolvedArgs.add(this.getMessage(messageSourceResolvable, locale));
         } else {
            resolvedArgs.add(arg);
         }
      }

      return resolvedArgs.toArray();
   }

   protected @Nullable String resolveCodeWithoutArguments(String code, Locale locale) {
      MessageFormat messageFormat = this.resolveCode(code, locale);
      if (messageFormat != null) {
         synchronized (messageFormat) {
            return messageFormat.format(new Object[0]);
         }
      } else {
         return null;
      }
   }

   protected abstract @Nullable MessageFormat resolveCode(String code, Locale locale);
}
