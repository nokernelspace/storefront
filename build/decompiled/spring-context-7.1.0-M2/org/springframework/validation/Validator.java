package org.springframework.validation;

import java.util.function.BiConsumer;

public interface Validator {
   boolean supports(Class<?> clazz);

   void validate(Object target, Errors errors);

   default Errors validateObject(Object target) {
      Errors errors = new SimpleErrors(target);
      this.validate(target, errors);
      return errors;
   }

   static <T> Validator forInstanceOf(Class<T> targetClass, BiConsumer<T, Errors> delegate) {
      return new TypedValidator<>(targetClass, targetClass::isAssignableFrom, delegate);
   }

   static <T> Validator forType(Class<T> targetClass, BiConsumer<T, Errors> delegate) {
      return new TypedValidator<>(targetClass, targetClass::equals, delegate);
   }
}
