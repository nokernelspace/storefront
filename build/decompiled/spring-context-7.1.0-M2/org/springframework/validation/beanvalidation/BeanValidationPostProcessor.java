package org.springframework.validation.beanvalidation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Iterator;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.Assert;

public class BeanValidationPostProcessor implements BeanPostProcessor, InitializingBean {
   private @Nullable Validator validator;
   private boolean afterInitialization = false;

   public void setValidator(Validator validator) {
      this.validator = validator;
   }

   public void setValidatorFactory(ValidatorFactory validatorFactory) {
      this.validator = validatorFactory.getValidator();
   }

   public void setAfterInitialization(boolean afterInitialization) {
      this.afterInitialization = afterInitialization;
   }

   public void afterPropertiesSet() {
      if (this.validator == null) {
         this.validator = Validation.buildDefaultValidatorFactory().getValidator();
      }
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      if (!this.afterInitialization) {
         this.doValidate(bean);
      }

      return bean;
   }

   public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
      if (this.afterInitialization) {
         this.doValidate(bean);
      }

      return bean;
   }

   protected void doValidate(Object bean) {
      Assert.state(this.validator != null, "No Validator set");
      Object objectToValidate = AopProxyUtils.ultimateSingletonTarget(bean);
      Set<ConstraintViolation<Object>> result = this.validator.validate(objectToValidate, new Class[0]);
      if (!result.isEmpty()) {
         StringBuilder sb = new StringBuilder("Bean state is invalid: ");
         Iterator<ConstraintViolation<Object>> it = result.iterator();

         while (it.hasNext()) {
            ConstraintViolation<Object> violation = it.next();
            sb.append(violation.getPropertyPath()).append(" - ").append(violation.getMessage());
            if (it.hasNext()) {
               sb.append("; ");
            }
         }

         throw new BeanInitializationException(sb.toString());
      }
   }
}
