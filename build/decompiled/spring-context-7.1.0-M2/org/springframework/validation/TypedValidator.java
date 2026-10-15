package org.springframework.validation;

import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.springframework.util.Assert;

final class TypedValidator<T> implements Validator {
   private final Class<T> targetClass;
   private final Predicate<Class<?>> supports;
   private final BiConsumer<T, Errors> validate;

   public TypedValidator(Class<T> targetClass, Predicate<Class<?>> supports, BiConsumer<T, Errors> validate) {
      Assert.notNull(targetClass, "TargetClass must not be null");
      Assert.notNull(supports, "Supports function must not be null");
      Assert.notNull(validate, "Validate function must not be null");
      this.targetClass = targetClass;
      this.supports = supports;
      this.validate = validate;
   }

   @Override
   public boolean supports(Class<?> clazz) {
      return this.supports.test(clazz);
   }

   @Override
   public void validate(Object target, Errors errors) {
      this.validate.accept(this.targetClass.cast(target), errors);
   }
}
