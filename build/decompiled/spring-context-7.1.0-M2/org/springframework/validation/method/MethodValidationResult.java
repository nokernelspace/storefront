package org.springframework.validation.method;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;

public interface MethodValidationResult {
   Object getTarget();

   Method getMethod();

   boolean isForReturnValue();

   default boolean hasErrors() {
      return !this.getParameterValidationResults().isEmpty();
   }

   default List<? extends MessageSourceResolvable> getAllErrors() {
      return this.getParameterValidationResults().stream().flatMap(result -> result.getResolvableErrors().stream()).toList();
   }

   List<ParameterValidationResult> getParameterValidationResults();

   default List<ParameterValidationResult> getValueResults() {
      return this.getParameterValidationResults().stream().filter(result -> !(result instanceof ParameterErrors)).toList();
   }

   default List<ParameterErrors> getBeanResults() {
      return this.getParameterValidationResults().stream().filter(ParameterErrors.class::isInstance).map(result -> (ParameterErrors)result).toList();
   }

   List<MessageSourceResolvable> getCrossParameterValidationResults();

   static MethodValidationResult create(Object target, Method method, List<ParameterValidationResult> results) {
      return create(target, method, results, Collections.emptyList());
   }

   static MethodValidationResult create(
      Object target, Method method, List<ParameterValidationResult> results, List<MessageSourceResolvable> crossParameterErrors
   ) {
      return new DefaultMethodValidationResult(target, method, results, crossParameterErrors);
   }

   static MethodValidationResult emptyResult() {
      return new EmptyMethodValidationResult();
   }
}
