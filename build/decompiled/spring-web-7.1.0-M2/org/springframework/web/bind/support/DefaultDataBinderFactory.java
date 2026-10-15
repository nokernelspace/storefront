package org.springframework.web.bind.support;

import jakarta.validation.Validator;
import java.lang.annotation.Annotation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.validation.DataBinder;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;

public class DefaultDataBinderFactory implements WebDataBinderFactory {
   private final @Nullable WebBindingInitializer initializer;
   private boolean methodValidationApplicable;

   public DefaultDataBinderFactory(@Nullable WebBindingInitializer initializer) {
      this.initializer = initializer;
   }

   public void setMethodValidationApplicable(boolean methodValidationApplicable) {
      this.methodValidationApplicable = methodValidationApplicable;
   }

   @Override
   public final WebDataBinder createBinder(NativeWebRequest webRequest, @Nullable Object target, String objectName) throws Exception {
      return this.createBinderInternal(webRequest, target, objectName, null);
   }

   @Override
   public final WebDataBinder createBinder(NativeWebRequest webRequest, @Nullable Object target, String objectName, ResolvableType type) throws Exception {
      return this.createBinderInternal(webRequest, target, objectName, type);
   }

   private WebDataBinder createBinderInternal(NativeWebRequest webRequest, @Nullable Object target, String objectName, @Nullable ResolvableType type) throws Exception {
      WebDataBinder dataBinder = this.createBinderInstance(target, objectName, webRequest);
      dataBinder.setNameResolver(new BindParamNameResolver());
      if (target == null && type != null) {
         dataBinder.setTargetType(type);
      }

      if (this.initializer != null) {
         this.initializer.initBinder(dataBinder);
      }

      this.initBinder(dataBinder, webRequest);
      if (this.methodValidationApplicable && type != null && type.getSource() instanceof MethodParameter parameter) {
         DefaultDataBinderFactory.MethodValidationInitializer.initBinder(dataBinder, parameter);
      }

      return dataBinder;
   }

   protected WebDataBinder createBinderInstance(@Nullable Object target, String objectName, NativeWebRequest webRequest) throws Exception {
      return new WebRequestDataBinder(target, objectName);
   }

   protected void initBinder(WebDataBinder dataBinder, NativeWebRequest webRequest) throws Exception {
   }

   private static class MethodValidationInitializer {
      public static void initBinder(DataBinder binder, MethodParameter parameter) {
         for (Annotation annotation : parameter.getParameterAnnotations()) {
            if (annotation.annotationType().getName().equals("jakarta.validation.Valid")) {
               binder.setExcludedValidators(v -> v instanceof Validator || v instanceof SmartValidator sv && sv.unwrap(Validator.class) != null);
            }
         }
      }
   }
}
