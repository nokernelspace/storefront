package org.springframework.web.context.request.async;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.WebRequest;

public abstract class WebAsyncUtils {
   public static final String WEB_ASYNC_MANAGER_ATTRIBUTE = WebAsyncManager.class.getName() + ".WEB_ASYNC_MANAGER";

   public static WebAsyncManager getAsyncManager(ServletRequest servletRequest) {
      WebAsyncManager asyncManager = null;
      if (servletRequest.getAttribute(WEB_ASYNC_MANAGER_ATTRIBUTE) instanceof WebAsyncManager wam) {
         asyncManager = wam;
      }

      if (asyncManager == null) {
         asyncManager = new WebAsyncManager();
         servletRequest.setAttribute(WEB_ASYNC_MANAGER_ATTRIBUTE, asyncManager);
      }

      return asyncManager;
   }

   public static WebAsyncManager getAsyncManager(WebRequest webRequest) {
      int scope = 0;
      WebAsyncManager asyncManager = null;
      if (webRequest.getAttribute(WEB_ASYNC_MANAGER_ATTRIBUTE, scope) instanceof WebAsyncManager wam) {
         asyncManager = wam;
      }

      if (asyncManager == null) {
         asyncManager = new WebAsyncManager();
         webRequest.setAttribute(WEB_ASYNC_MANAGER_ATTRIBUTE, asyncManager, scope);
      }

      return asyncManager;
   }

   public static AsyncWebRequest createAsyncWebRequest(HttpServletRequest request, HttpServletResponse response) {
      return getAsyncManager(request).getAsyncWebRequest() instanceof StandardServletAsyncWebRequest standardRequest
         ? new StandardServletAsyncWebRequest(request, response, standardRequest)
         : new StandardServletAsyncWebRequest(request, response);
   }
}
