package org.springframework.web.service.invoker;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestAttribute;

public class RequestAttributeArgumentResolver extends AbstractNamedValueArgumentResolver {
   @Override
   protected AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      RequestAttribute annot = (RequestAttribute)parameter.getParameterAnnotation(RequestAttribute.class);
      return annot == null ? null : new AbstractNamedValueArgumentResolver.NamedValueInfo(annot.name(), annot.required(), null, "request attribute", false);
   }

   @Override
   protected void addRequestValue(String name, Object value, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      requestValues.addAttribute(name, value);
   }
}
