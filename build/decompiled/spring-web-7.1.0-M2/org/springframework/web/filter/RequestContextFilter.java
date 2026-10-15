package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestContextFilter extends OncePerRequestFilter {
   private boolean threadContextInheritable = false;

   public void setThreadContextInheritable(boolean threadContextInheritable) {
      this.threadContextInheritable = threadContextInheritable;
   }

   @Override
   protected boolean shouldNotFilterAsyncDispatch() {
      return false;
   }

   @Override
   protected boolean shouldNotFilterErrorDispatch() {
      return false;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
      this.initContextHolders(request, attributes);

      try {
         filterChain.doFilter(request, response);
      } finally {
         this.resetContextHolders();
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Cleared thread-bound request context: " + request);
         }

         attributes.requestCompleted();
      }
   }

   private void initContextHolders(HttpServletRequest request, ServletRequestAttributes requestAttributes) {
      LocaleContextHolder.setLocale(request.getLocale(), this.threadContextInheritable);
      RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Bound request context to thread: " + request);
      }
   }

   private void resetContextHolders() {
      LocaleContextHolder.resetLocaleContext();
      RequestContextHolder.resetRequestAttributes();
   }
}
