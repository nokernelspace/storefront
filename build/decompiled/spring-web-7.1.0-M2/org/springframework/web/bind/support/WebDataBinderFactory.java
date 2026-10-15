package org.springframework.web.bind.support;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;

public interface WebDataBinderFactory {
   WebDataBinder createBinder(NativeWebRequest webRequest, @Nullable Object target, String objectName) throws Exception;

   default WebDataBinder createBinder(NativeWebRequest webRequest, @Nullable Object target, String objectName, ResolvableType targetType) throws Exception {
      return this.createBinder(webRequest, target, objectName);
   }
}
