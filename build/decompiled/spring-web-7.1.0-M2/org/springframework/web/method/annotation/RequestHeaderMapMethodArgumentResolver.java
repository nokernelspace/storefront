package org.springframework.web.method.annotation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class RequestHeaderMapMethodArgumentResolver implements HandlerMethodArgumentResolver {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(RequestHeader.class)
         && (Map.class.isAssignableFrom(parameter.getParameterType()) || HttpHeaders.class.isAssignableFrom(parameter.getParameterType()));
   }

   @Override
   public Object resolveArgument(
      MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory
   ) throws Exception {
      Class<?> paramType = parameter.getParameterType();
      if (HttpHeaders.class.isAssignableFrom(paramType)) {
         HttpHeaders result = new HttpHeaders();
         this.copyHeaderValues(webRequest, result::add);
         return result;
      }

      if (MultiValueMap.class.isAssignableFrom(paramType)) {
         MultiValueMap<Object, Object> result = new LinkedMultiValueMap();
         this.copyHeaderValues(webRequest, result::add);
         return result;
      }

      Map<String, String> result = new LinkedHashMap<>();
      Iterator<String> iterator = webRequest.getHeaderNames();

      while (iterator.hasNext()) {
         String headerName = iterator.next();
         String headerValue = webRequest.getHeader(headerName);
         if (headerValue != null) {
            result.put(headerName, headerValue);
         }
      }

      return result;
   }

   private void copyHeaderValues(NativeWebRequest webRequest, BiConsumer<String, String> consumer) {
      Iterator<String> iterator = webRequest.getHeaderNames();

      while (iterator.hasNext()) {
         String headerName = iterator.next();
         String[] headerValues = webRequest.getHeaderValues(headerName);
         if (headerValues != null) {
            for (String headerValue : headerValues) {
               consumer.accept(headerName, headerValue);
            }
         }
      }
   }
}
