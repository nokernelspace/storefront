package org.springframework.web.bind.annotation;

import java.lang.reflect.Type;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;

class ExceptionHandlerReflectiveProcessor extends ControllerMappingReflectiveProcessor {
   @Override
   protected void registerReturnTypeHints(ReflectionHints hints, MethodParameter returnTypeParameter) {
      Class<?> returnType = returnTypeParameter.getParameterType();
      if (ProblemDetail.class.isAssignableFrom(returnType)) {
         this.getBindingRegistrar().registerReflectionHints(hints, new Type[]{returnTypeParameter.getGenericParameterType()});
      }

      super.registerReturnTypeHints(hints, returnTypeParameter);
   }
}
