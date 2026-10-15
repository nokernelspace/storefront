package org.springframework.web.method.annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;

public class RequestParamMapMethodArgumentResolver implements HandlerMethodArgumentResolver {
   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      RequestParam requestParam = (RequestParam)parameter.getParameterAnnotation(RequestParam.class);
      return requestParam != null && Map.class.isAssignableFrom(parameter.getParameterType()) && !StringUtils.hasText(requestParam.name());
   }

   @Override
   public Object resolveArgument(
      MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory
   ) throws Exception {
      ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
      if (!MultiValueMap.class.isAssignableFrom(parameter.getParameterType())) {
         Class<?> valueType = resolvableType.asMap().getGeneric(new int[]{1}).resolve();
         if (valueType == MultipartFile.class) {
            MultipartRequest multipartRequest = MultipartResolutionDelegate.resolveMultipartRequest(webRequest);
            return multipartRequest != null ? multipartRequest.getFileMap() : new LinkedHashMap(0);
         }

         if (valueType == Part.class) {
            HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
            if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
               Collection<Part> parts = servletRequest.getParts();
               LinkedHashMap<String, Part> result = CollectionUtils.newLinkedHashMap(parts.size());

               for (Part part : parts) {
                  if (!result.containsKey(part.getName())) {
                     result.put(part.getName(), part);
                  }
               }

               return result;
            } else {
               return new LinkedHashMap(0);
            }
         } else {
            Map<String, String[]> parameterMap = webRequest.getParameterMap();
            Map<String, String> result = CollectionUtils.newLinkedHashMap(parameterMap.size());
            parameterMap.forEach((key, values) -> {
               if (values.length > 0) {
                  result.put(key, values[0]);
               }
            });
            return result;
         }
      } else {
         Class<?> valueType = resolvableType.as(MultiValueMap.class).getGeneric(new int[]{1}).resolve();
         if (valueType == MultipartFile.class) {
            MultipartRequest multipartRequest = MultipartResolutionDelegate.resolveMultipartRequest(webRequest);
            return multipartRequest != null ? multipartRequest.getMultiFileMap() : new LinkedMultiValueMap(0);
         }

         if (valueType != Part.class) {
            Map<String, String[]> parameterMap = webRequest.getParameterMap();
            MultiValueMap<String, String> result = new LinkedMultiValueMap(parameterMap.size());
            parameterMap.forEach((key, values) -> {
               for (String value : values) {
                  result.add(key, value);
               }
            });
            return result;
         }

         HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
         if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
            Collection<Part> parts = servletRequest.getParts();
            LinkedMultiValueMap<String, Part> result = new LinkedMultiValueMap(parts.size());

            for (Part part : parts) {
               result.add(part.getName(), part);
            }

            return result;
         } else {
            return new LinkedMultiValueMap(0);
         }
      }
   }
}
