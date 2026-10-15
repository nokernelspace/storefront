package org.springframework.web.context.request;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;

public class RequestContextListener implements ServletRequestListener {
   private static final String REQUEST_ATTRIBUTES_ATTRIBUTE = RequestContextListener.class.getName() + ".REQUEST_ATTRIBUTES";

   public void requestInitialized(ServletRequestEvent requestEvent) {
      if (requestEvent.getServletRequest() instanceof HttpServletRequest request) {
         ServletRequestAttributes var4 = new ServletRequestAttributes(request);
         request.setAttribute(REQUEST_ATTRIBUTES_ATTRIBUTE, var4);
         LocaleContextHolder.setLocale(request.getLocale());
         RequestContextHolder.setRequestAttributes(var4);
      } else {
         throw new IllegalArgumentException("Request is not an HttpServletRequest: " + requestEvent.getServletRequest());
      }
   }

   public void requestDestroyed(ServletRequestEvent requestEvent) {
      ServletRequestAttributes attributes = null;
      if (requestEvent.getServletRequest().getAttribute(REQUEST_ATTRIBUTES_ATTRIBUTE) instanceof ServletRequestAttributes servletRequestAttributes) {
         attributes = servletRequestAttributes;
      }

      RequestAttributes threadAttributes = RequestContextHolder.getRequestAttributes();
      if (threadAttributes != null) {
         LocaleContextHolder.resetLocaleContext();
         RequestContextHolder.resetRequestAttributes();
         if (attributes == null && threadAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            attributes = servletRequestAttributes;
         }
      }

      if (attributes != null) {
         attributes.requestCompleted();
      }
   }
}
