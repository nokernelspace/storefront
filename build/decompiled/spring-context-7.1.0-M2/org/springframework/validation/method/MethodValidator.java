package org.springframework.validation.method;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;

public interface MethodValidator {
   @Deprecated(since = "7.0.4", forRemoval = true)
   Class<?>[] determineValidationGroups(Object target, Method method);

   MethodValidationResult validateArguments(
      Object target, Method method, MethodParameter @Nullable [] parameters, @Nullable Object[] arguments, Class<?>[] groups
   );

   default void applyArgumentValidation(Object target, Method method, MethodParameter @Nullable [] parameters, @Nullable Object[] arguments, Class<?>[] groups) {
      MethodValidationResult result = this.validateArguments(target, method, parameters, arguments, groups);
      if (result.hasErrors()) {
         throw new MethodValidationException(result);
      }
   }

   MethodValidationResult validateReturnValue(
      Object target, Method method, @Nullable MethodParameter returnType, @Nullable Object returnValue, Class<?>[] groups
   );

   default void applyReturnValueValidation(Object target, Method method, @Nullable MethodParameter returnType, @Nullable Object returnValue, Class<?>[] groups) {
      MethodValidationResult result = this.validateReturnValue(target, method, returnType, returnValue, groups);
      if (result.hasErrors()) {
         throw new MethodValidationException(result);
      }
   }
}
