package org.springframework.web.filter;

import jakarta.servlet.http.HttpServletRequest;

public class ServletContextRequestLoggingFilter extends AbstractRequestLoggingFilter {
   @Override
   protected void beforeRequest(HttpServletRequest request, String message) {
      this.getServletContext().log(message);
   }

   @Override
   protected void afterRequest(HttpServletRequest request, String message) {
      this.getServletContext().log(message);
   }
}
