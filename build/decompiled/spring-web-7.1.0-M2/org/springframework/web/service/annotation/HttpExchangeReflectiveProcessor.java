package org.springframework.web.service.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.annotation.ReflectiveProcessor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestBody;

class HttpExchangeReflectiveProcessor implements ReflectiveProcessor {
   private final BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();

   public void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
      if (element instanceof Method method) {
         this.registerMethodHints(hints, method);
      }
   }

   protected void registerMethodHints(ReflectionHints hints, Method method) {
      hints.registerMethod(method, ExecutableMode.INVOKE);

      for (Parameter parameter : method.getParameters()) {
         this.registerParameterTypeHints(hints, MethodParameter.forParameter(parameter));
      }

      this.registerReturnTypeHints(hints, MethodParameter.forExecutable(method, -1));
   }

   protected void registerParameterTypeHints(ReflectionHints hints, MethodParameter methodParameter) {
      if (methodParameter.hasParameterAnnotation(RequestBody.class)) {
         this.bindingRegistrar.registerReflectionHints(hints, new Type[]{methodParameter.getGenericParameterType()});
      }
   }

   protected void registerReturnTypeHints(ReflectionHints hints, MethodParameter returnTypeParameter) {
      if (!void.class.equals(returnTypeParameter.getParameterType())) {
         this.bindingRegistrar.registerReflectionHints(hints, new Type[]{returnTypeParameter.getGenericParameterType()});
      }
   }
}
