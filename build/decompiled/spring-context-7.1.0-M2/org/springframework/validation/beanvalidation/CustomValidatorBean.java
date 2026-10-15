package org.springframework.validation.beanvalidation;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.ValidatorFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;

public class CustomValidatorBean extends SpringValidatorAdapter implements Validator, InitializingBean {
   private @Nullable ValidatorFactory validatorFactory;
   private @Nullable MessageInterpolator messageInterpolator;
   private @Nullable TraversableResolver traversableResolver;

   public void setValidatorFactory(ValidatorFactory validatorFactory) {
      this.validatorFactory = validatorFactory;
   }

   public void setMessageInterpolator(MessageInterpolator messageInterpolator) {
      this.messageInterpolator = messageInterpolator;
   }

   public void setTraversableResolver(TraversableResolver traversableResolver) {
      this.traversableResolver = traversableResolver;
   }

   public void afterPropertiesSet() {
      if (this.validatorFactory == null) {
         this.validatorFactory = Validation.buildDefaultValidatorFactory();
      }

      ValidatorContext validatorContext = this.validatorFactory.usingContext();
      MessageInterpolator targetInterpolator = this.messageInterpolator;
      if (targetInterpolator == null) {
         targetInterpolator = this.validatorFactory.getMessageInterpolator();
      }

      validatorContext.messageInterpolator(new LocaleContextMessageInterpolator(targetInterpolator));
      if (this.traversableResolver != null) {
         validatorContext.traversableResolver(this.traversableResolver);
      }

      this.setTargetValidator(validatorContext.getValidator());
   }
}
