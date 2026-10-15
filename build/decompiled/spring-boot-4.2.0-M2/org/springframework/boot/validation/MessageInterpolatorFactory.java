package org.springframework.boot.validation;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.MessageSource;
import org.springframework.util.ClassUtils;

public class MessageInterpolatorFactory implements ObjectFactory<MessageInterpolator> {
   private static final Set<String> FALLBACKS;
   private final @Nullable MessageSource messageSource;

   public MessageInterpolatorFactory() {
      this(null);
   }

   public MessageInterpolatorFactory(@Nullable MessageSource messageSource) {
      this.messageSource = messageSource;
   }

   public MessageInterpolator getObject() throws BeansException {
      MessageInterpolator messageInterpolator = this.getMessageInterpolator();
      return this.messageSource != null ? new MessageSourceMessageInterpolator(this.messageSource, messageInterpolator) : messageInterpolator;
   }

   private MessageInterpolator getMessageInterpolator() {
      try {
         return Validation.byDefaultProvider().configure().getDefaultMessageInterpolator();
      } catch (ValidationException ex) {
         MessageInterpolator fallback = this.getFallback();
         if (fallback != null) {
            return fallback;
         } else {
            throw ex;
         }
      }
   }

   private @Nullable MessageInterpolator getFallback() {
      for (String fallback : FALLBACKS) {
         try {
            return this.getFallback(fallback);
         } catch (Exception var4) {
         }
      }

      return null;
   }

   private MessageInterpolator getFallback(String fallback) {
      Class<?> interpolatorClass = ClassUtils.resolveClassName(fallback, null);
      Object interpolator = BeanUtils.instantiateClass(interpolatorClass);
      return (MessageInterpolator)interpolator;
   }

   static {
      Set<String> fallbacks = new LinkedHashSet<>();
      fallbacks.add("org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator");
      FALLBACKS = Collections.unmodifiableSet(fallbacks);
   }
}
