package org.springframework.web.bind.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.annotation.ReflectiveProcessor;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpEntity;
import org.springframework.util.ReflectionUtils;

class ControllerMappingReflectiveProcessor implements ReflectiveProcessor {
   private final BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();

   public void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
      if (element instanceof Class<?> type) {
         this.registerTypeHints(hints, type);
      } else if (element instanceof Method method) {
         this.registerMethodHints(hints, method);
      }
   }

   protected final BindingReflectionHintsRegistrar getBindingRegistrar() {
      return this.bindingRegistrar;
   }

   protected void registerTypeHints(ReflectionHints hints, Class<?> type) {
      hints.registerType(type, new MemberCategory[0]);
   }

   protected void registerMethodHints(ReflectionHints hints, Method method) {
      hints.registerMethod(method, ExecutableMode.INVOKE);
      Class<?> declaringClass = method.getDeclaringClass();
      if (KotlinDetector.isKotlinType(declaringClass)) {
         ReflectionUtils.doWithMethods(
            declaringClass, m -> hints.registerMethod(m, ExecutableMode.INVOKE), m -> m.getName().equals(method.getName() + "$default")
         );
      }

      for (Parameter parameter : method.getParameters()) {
         this.registerParameterTypeHints(hints, MethodParameter.forParameter(parameter));
      }

      this.registerReturnTypeHints(hints, MethodParameter.forExecutable(method, -1));
   }

   protected void registerParameterTypeHints(ReflectionHints hints, MethodParameter methodParameter) {
      if (methodParameter.hasParameterAnnotation(RequestBody.class)
         || methodParameter.hasParameterAnnotation(ModelAttribute.class)
         || methodParameter.hasParameterAnnotation(RequestPart.class)) {
         this.bindingRegistrar.registerReflectionHints(hints, new Type[]{methodParameter.getGenericParameterType()});
      } else if (HttpEntity.class.isAssignableFrom(methodParameter.getParameterType())) {
         Type httpEntityType = this.getHttpEntityType(methodParameter);
         if (httpEntityType != null) {
            this.bindingRegistrar.registerReflectionHints(hints, new Type[]{httpEntityType});
         }
      }
   }

   protected void registerReturnTypeHints(ReflectionHints hints, MethodParameter returnTypeParameter) {
      if (AnnotatedElementUtils.hasAnnotation(returnTypeParameter.getContainingClass(), ResponseBody.class)
         || returnTypeParameter.hasMethodAnnotation(ResponseBody.class)) {
         this.bindingRegistrar.registerReflectionHints(hints, new Type[]{returnTypeParameter.getGenericParameterType()});
      } else if (HttpEntity.class.isAssignableFrom(returnTypeParameter.getParameterType())) {
         Type httpEntityType = this.getHttpEntityType(returnTypeParameter);
         if (httpEntityType != null) {
            this.bindingRegistrar.registerReflectionHints(hints, new Type[]{httpEntityType});
         }
      }
   }

   private @Nullable Type getHttpEntityType(MethodParameter parameter) {
      MethodParameter nestedParameter = parameter.nested();
      return nestedParameter.getNestedParameterType() == nestedParameter.getParameterType() ? null : nestedParameter.getNestedParameterType();
   }
}
