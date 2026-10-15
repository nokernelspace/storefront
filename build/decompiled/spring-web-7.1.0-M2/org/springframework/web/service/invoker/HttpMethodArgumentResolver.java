package org.springframework.web.service.invoker;

import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

public class HttpMethodArgumentResolver implements HttpServiceArgumentResolver {
   private static final Log logger = LogFactory.getLog(HttpMethodArgumentResolver.class);

   @Override
   public boolean resolve(@Nullable Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      parameter = parameter.nestedIfOptional();
      if (!parameter.getNestedParameterType().equals(HttpMethod.class)) {
         return false;
      }

      if (argument instanceof Optional<?> optionalValue) {
         argument = optionalValue.orElse(null);
      }

      if (argument == null) {
         Assert.isTrue(parameter.isOptional(), "HttpMethod is required");
         return true;
      }

      HttpMethod httpMethod = (HttpMethod)argument;
      requestValues.setHttpMethod(httpMethod);
      if (logger.isTraceEnabled()) {
         logger.trace("Resolved HTTP method to: " + httpMethod.name());
      }

      return true;
   }
}
