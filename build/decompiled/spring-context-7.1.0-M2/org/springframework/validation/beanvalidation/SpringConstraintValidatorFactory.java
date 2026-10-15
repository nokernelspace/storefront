package org.springframework.validation.beanvalidation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.util.Assert;

public class SpringConstraintValidatorFactory implements ConstraintValidatorFactory {
   private final AutowireCapableBeanFactory beanFactory;
   private final @Nullable ConstraintValidatorFactory defaultConstraintValidatorFactory;

   public SpringConstraintValidatorFactory(AutowireCapableBeanFactory beanFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
      this.defaultConstraintValidatorFactory = null;
   }

   public SpringConstraintValidatorFactory(AutowireCapableBeanFactory beanFactory, ConstraintValidatorFactory defaultConstraintValidatorFactory) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
      this.defaultConstraintValidatorFactory = defaultConstraintValidatorFactory;
   }

   public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
      if (this.defaultConstraintValidatorFactory != null) {
         String providerModuleName = this.defaultConstraintValidatorFactory.getClass().getModule().getName();
         if (providerModuleName != null && providerModuleName.equals(key.getModule().getName())) {
            return (T)this.defaultConstraintValidatorFactory.getInstance(key);
         }
      }

      return (T)this.beanFactory.createBean(key);
   }

   public void releaseInstance(ConstraintValidator<?, ?> instance) {
      this.beanFactory.destroyBean(instance);
   }
}
