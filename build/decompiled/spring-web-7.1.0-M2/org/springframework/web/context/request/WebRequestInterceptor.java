package org.springframework.web.context.request;

import org.jspecify.annotations.Nullable;
import org.springframework.ui.ModelMap;

public interface WebRequestInterceptor {
   void preHandle(WebRequest request) throws Exception;

   void postHandle(WebRequest request, @Nullable ModelMap model) throws Exception;

   void afterCompletion(WebRequest request, @Nullable Exception ex) throws Exception;
}
