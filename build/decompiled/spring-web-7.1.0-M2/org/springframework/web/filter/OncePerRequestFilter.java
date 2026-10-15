package org.springframework.web.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.context.request.async.WebAsyncUtils;

public abstract class OncePerRequestFilter extends GenericFilterBean {
   public static final String ALREADY_FILTERED_SUFFIX = ".FILTERED";

   public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
         String alreadyFilteredAttributeName = this.getAlreadyFilteredAttributeName();
         boolean hasAlreadyFilteredAttribute = request.getAttribute(alreadyFilteredAttributeName) != null;
         if (this.skipDispatch(httpRequest) || this.shouldNotFilter(httpRequest)) {
            filterChain.doFilter(request, response);
         } else if (hasAlreadyFilteredAttribute) {
            if (DispatcherType.ERROR.equals(request.getDispatcherType())) {
               this.doFilterNestedErrorDispatch(httpRequest, httpResponse, filterChain);
               return;
            }

            filterChain.doFilter(request, response);
         } else {
            request.setAttribute(alreadyFilteredAttributeName, Boolean.TRUE);

            try {
               this.doFilterInternal(httpRequest, httpResponse, filterChain);
            } finally {
               request.removeAttribute(alreadyFilteredAttributeName);
            }
         }
      } else {
         throw new ServletException("OncePerRequestFilter only supports HTTP requests");
      }
   }

   private boolean skipDispatch(HttpServletRequest request) {
      return this.isAsyncDispatch(request) && this.shouldNotFilterAsyncDispatch()
         ? true
         : request.getAttribute("jakarta.servlet.error.request_uri") != null && this.shouldNotFilterErrorDispatch();
   }

   protected boolean isAsyncDispatch(HttpServletRequest request) {
      return DispatcherType.ASYNC.equals(request.getDispatcherType());
   }

   protected boolean isAsyncStarted(HttpServletRequest request) {
      return WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted();
   }

   protected String getAlreadyFilteredAttributeName() {
      String name = this.getFilterName();
      if (name == null) {
         name = this.getClass().getName();
      }

      return name + ".FILTERED";
   }

   protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
      return false;
   }

   protected boolean shouldNotFilterAsyncDispatch() {
      return true;
   }

   protected boolean shouldNotFilterErrorDispatch() {
      return true;
   }

   protected abstract void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException;

   protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      filterChain.doFilter(request, response);
   }
}
