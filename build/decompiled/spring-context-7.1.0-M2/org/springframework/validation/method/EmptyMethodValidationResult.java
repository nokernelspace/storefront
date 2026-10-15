package org.springframework.validation.method;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;

final class EmptyMethodValidationResult implements MethodValidationResult {
   @Override
   public Object getTarget() {
      throw new UnsupportedOperationException();
   }

   @Override
   public Method getMethod() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isForReturnValue() {
      throw new UnsupportedOperationException();
   }

   @Override
   public List<ParameterValidationResult> getParameterValidationResults() {
      return Collections.emptyList();
   }

   @Override
   public List<MessageSourceResolvable> getCrossParameterValidationResults() {
      return Collections.emptyList();
   }

   @Override
   public String toString() {
      return "0 validation errors";
   }
}
