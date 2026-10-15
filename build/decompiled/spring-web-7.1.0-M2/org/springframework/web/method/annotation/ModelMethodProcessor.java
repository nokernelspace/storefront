package org.springframework.web.method.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

public class ModelMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return Model.class.isAssignableFrom(parameter.getParameterType());
   }

   @Override
   public @Nullable Object resolveArgument(
      MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory
   ) throws Exception {
      Assert.state(mavContainer != null, "ModelAndViewContainer is required for model exposure");
      return mavContainer.getModel();
   }

   @Override
   public boolean supportsReturnType(MethodParameter returnType) {
      return Model.class.isAssignableFrom(returnType.getParameterType());
   }

   @Override
   public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
      if (returnValue != null) {
         if (returnValue instanceof Model model) {
            mavContainer.addAllAttributes(model.asMap());
         } else {
            throw new UnsupportedOperationException(
               "Unexpected return type [" + returnType.getParameterType().getName() + "] in method: " + returnType.getMethod()
            );
         }
      }
   }
}
