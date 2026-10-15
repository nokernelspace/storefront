package org.springframework.web.method.annotation;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.NativeWebRequest;

public class RequestHeaderMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {
   public RequestHeaderMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
      super(beanFactory);
   }

   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(RequestHeader.class)
         && !Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())
         && !HttpHeaders.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType());
   }

   @Override
   protected AbstractNamedValueMethodArgumentResolver.NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      RequestHeader ann = (RequestHeader)parameter.getParameterAnnotation(RequestHeader.class);
      Assert.state(ann != null, "No RequestHeader annotation");
      return new RequestHeaderMethodArgumentResolver.RequestHeaderNamedValueInfo(ann);
   }

   @Override
   protected @Nullable Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      String[] headerValues = request.getHeaderValues(name);
      if (headerValues != null) {
         return headerValues.length == 1 ? headerValues[0] : headerValues;
      } else {
         return null;
      }
   }

   @Override
   protected void handleMissingValue(String name, MethodParameter parameter) throws ServletRequestBindingException {
      throw new MissingRequestHeaderException(name, parameter);
   }

   @Override
   protected void handleMissingValueAfterConversion(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      throw new MissingRequestHeaderException(name, parameter, true);
   }

   private static final class RequestHeaderNamedValueInfo extends AbstractNamedValueMethodArgumentResolver.NamedValueInfo {
      private RequestHeaderNamedValueInfo(RequestHeader annotation) {
         super(annotation.name(), annotation.required(), annotation.defaultValue());
      }
   }
}
