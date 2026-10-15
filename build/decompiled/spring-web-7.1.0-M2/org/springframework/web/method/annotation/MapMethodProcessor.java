package org.springframework.web.method.annotation;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

public class MapMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      Class<?> type = parameter.getParameterType();
      return (type.isAssignableFrom(Map.class) || ModelMap.class.isAssignableFrom(type)) && parameter.getParameterAnnotations().length == 0;
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
      return Map.class.isAssignableFrom(returnType.getParameterType());
   }

   @Override
   public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
      if (returnValue instanceof Map map) {
         mavContainer.addAllAttributes(map);
      } else if (returnValue != null) {
         throw new UnsupportedOperationException(
            "Unexpected return type [" + returnType.getParameterType().getName() + "] in method: " + returnType.getMethod()
         );
      }
   }
}
