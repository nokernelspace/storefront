package org.springframework.validation.beanvalidation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.annotation.Annotation;
import java.util.function.Supplier;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.AbstractBeanFactoryAwareAdvisingPostProcessor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.validation.annotation.Validated;

public class MethodValidationPostProcessor extends AbstractBeanFactoryAwareAdvisingPostProcessor implements InitializingBean {
   private Class<? extends Annotation> validatedAnnotationType = Validated.class;
   private Supplier<Validator> validator = SingletonSupplier.of(() -> Validation.buildDefaultValidatorFactory().getValidator());
   private boolean adaptConstraintViolations;

   public void setValidatedAnnotationType(Class<? extends Annotation> validatedAnnotationType) {
      Assert.notNull(validatedAnnotationType, "'validatedAnnotationType' must not be null");
      this.validatedAnnotationType = validatedAnnotationType;
   }

   public void setValidatorFactory(ValidatorFactory validatorFactory) {
      this.validator = SingletonSupplier.of(validatorFactory::getValidator);
   }

   public void setValidator(Validator validator) {
      this.validator = () -> validator;
   }

   public void setValidatorProvider(ObjectProvider<Validator> validatorProvider) {
      this.validator = validatorProvider::getObject;
   }

   public void setAdaptConstraintViolations(boolean adaptViolations) {
      this.adaptConstraintViolations = adaptViolations;
   }

   public void afterPropertiesSet() {
      Pointcut pointcut = new AnnotationMatchingPointcut(this.validatedAnnotationType, true);
      this.advisor = new DefaultPointcutAdvisor(pointcut, this.createMethodValidationAdvice(this.validator));
   }

   protected Advice createMethodValidationAdvice(Supplier<Validator> validator) {
      return new MethodValidationInterceptor(validator, this.adaptConstraintViolations);
   }
}
