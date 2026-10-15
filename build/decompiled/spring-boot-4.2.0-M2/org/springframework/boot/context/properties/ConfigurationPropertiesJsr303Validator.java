package org.springframework.boot.context.properties;

import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

final class ConfigurationPropertiesJsr303Validator implements Validator {
   private static final String[] VALIDATOR_CLASSES = new String[]{
      "jakarta.validation.Validator", "jakarta.validation.ValidatorFactory", "jakarta.validation.bootstrap.GenericBootstrap"
   };
   private final ConfigurationPropertiesJsr303Validator.Delegate delegate;
   private final Class<?> validatedType;

   ConfigurationPropertiesJsr303Validator(ApplicationContext applicationContext, Class<?> validatedType) {
      this.delegate = new ConfigurationPropertiesJsr303Validator.Delegate(applicationContext);
      this.validatedType = validatedType;
   }

   public boolean supports(Class<?> type) {
      return this.validatedType.equals(type) && this.delegate.supports(type);
   }

   public void validate(Object target, Errors errors) {
      this.delegate.validate(target, errors);
   }

   static boolean isJsr303Present(ApplicationContext applicationContext) {
      ClassLoader classLoader = applicationContext.getClassLoader();

      for (String validatorClass : VALIDATOR_CLASSES) {
         if (!ClassUtils.isPresent(validatorClass, classLoader)) {
            return false;
         }
      }

      return true;
   }

   private static class Delegate extends LocalValidatorFactoryBean {
      Delegate(ApplicationContext applicationContext) {
         this.setApplicationContext(applicationContext);
         this.setMessageInterpolator(new MessageInterpolatorFactory(applicationContext).getObject());
         this.afterPropertiesSet();
      }
   }
}
