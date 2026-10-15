package org.springframework.web.service.invoker;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.bind.annotation.PathVariable;

public class PathVariableArgumentResolver extends AbstractNamedValueArgumentResolver {
   public PathVariableArgumentResolver(ConversionService conversionService) {
      super(conversionService);
   }

   @Override
   protected AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      PathVariable annot = (PathVariable)parameter.getParameterAnnotation(PathVariable.class);
      return annot == null ? null : new AbstractNamedValueArgumentResolver.NamedValueInfo(annot.name(), annot.required(), null, "path variable", false);
   }

   @Override
   protected void addRequestValue(String name, Object value, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      requestValues.setUriVariable(name, (String)value);
   }
}
