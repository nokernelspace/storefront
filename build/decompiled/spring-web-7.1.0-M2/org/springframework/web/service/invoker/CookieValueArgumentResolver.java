package org.springframework.web.service.invoker;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.bind.annotation.CookieValue;

public class CookieValueArgumentResolver extends AbstractNamedValueArgumentResolver {
   public CookieValueArgumentResolver(ConversionService conversionService) {
      super(conversionService);
   }

   @Override
   protected AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      CookieValue annot = (CookieValue)parameter.getParameterAnnotation(CookieValue.class);
      return annot == null
         ? null
         : new AbstractNamedValueArgumentResolver.NamedValueInfo(annot.name(), annot.required(), annot.defaultValue(), "cookie value", true);
   }

   @Override
   protected void addRequestValue(String name, Object value, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      requestValues.addCookie(name, (String)value);
   }
}
