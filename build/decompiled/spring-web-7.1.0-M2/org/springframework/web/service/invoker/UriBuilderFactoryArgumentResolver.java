package org.springframework.web.service.invoker;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.web.util.UriBuilderFactory;

public class UriBuilderFactoryArgumentResolver implements HttpServiceArgumentResolver {
   @Override
   public boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      parameter = parameter.nestedIfOptional();
      if (!parameter.getNestedParameterType().equals(UriBuilderFactory.class)) {
         return false;
      }

      if (argument instanceof Optional<?> optionalValue) {
         argument = optionalValue.orElse(null);
      }

      if (argument == null) {
         Assert.isTrue(parameter.isOptional(), "UriBuilderFactory is required");
         return true;
      } else {
         requestValues.setUriBuilderFactory((UriBuilderFactory)argument);
         return true;
      }
   }
}
