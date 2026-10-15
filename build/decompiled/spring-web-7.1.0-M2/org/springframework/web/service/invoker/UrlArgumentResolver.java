package org.springframework.web.service.invoker;

import java.net.URI;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;

public class UrlArgumentResolver implements HttpServiceArgumentResolver {
   @Override
   public boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      parameter = parameter.nestedIfOptional();
      if (!parameter.getNestedParameterType().equals(URI.class)) {
         return false;
      }

      if (argument instanceof Optional<?> optionalValue) {
         argument = optionalValue.orElse(null);
      }

      if (argument == null) {
         Assert.isTrue(parameter.isOptional(), "URI is required");
         return true;
      } else {
         requestValues.setUri((URI)argument);
         return true;
      }
   }
}
