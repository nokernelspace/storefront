package org.springframework.validation;

import org.jspecify.annotations.Nullable;

public interface SmartValidator extends Validator {
   void validate(Object target, Errors errors, Object... validationHints);

   default void validateValue(Class<?> targetType, @Nullable String fieldName, @Nullable Object value, Errors errors, Object... validationHints) {
      throw new IllegalArgumentException("Cannot validate individual value for " + targetType);
   }

   default <T> @Nullable T unwrap(@Nullable Class<T> type) {
      return null;
   }
}
