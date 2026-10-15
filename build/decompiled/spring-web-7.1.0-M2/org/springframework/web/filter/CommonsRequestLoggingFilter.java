package org.springframework.web.filter;

import jakarta.servlet.http.HttpServletRequest;

public class CommonsRequestLoggingFilter extends AbstractRequestLoggingFilter {
   @Override
   protected boolean shouldLog(HttpServletRequest request) {
      return this.logger.isDebugEnabled();
   }

   @Override
   protected void beforeRequest(HttpServletRequest request, String message) {
      this.logger.debug(message);
   }

   @Override
   protected void afterRequest(HttpServletRequest request, String message) {
      this.logger.debug(message);
   }
}
