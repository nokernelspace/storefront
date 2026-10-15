package org.springframework.web.multipart.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

public class MultipartFilter extends OncePerRequestFilter {
   public static final String DEFAULT_MULTIPART_RESOLVER_BEAN_NAME = "filterMultipartResolver";
   private final MultipartResolver defaultMultipartResolver = new StandardServletMultipartResolver();
   private String multipartResolverBeanName = "filterMultipartResolver";

   public void setMultipartResolverBeanName(String multipartResolverBeanName) {
      this.multipartResolverBeanName = multipartResolverBeanName;
   }

   protected String getMultipartResolverBeanName() {
      return this.multipartResolverBeanName;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      MultipartResolver multipartResolver = this.lookupMultipartResolver(request);
      HttpServletRequest processedRequest = request;
      if (multipartResolver.isMultipart(processedRequest)) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Resolving multipart request");
         }

         processedRequest = multipartResolver.resolveMultipart(processedRequest);
      } else if (this.logger.isTraceEnabled()) {
         this.logger.trace("Not a multipart request");
      }

      try {
         filterChain.doFilter(processedRequest, response);
      } finally {
         if (processedRequest instanceof MultipartHttpServletRequest multipartRequest) {
            multipartResolver.cleanupMultipart(multipartRequest);
         }
      }
   }

   protected MultipartResolver lookupMultipartResolver(HttpServletRequest request) {
      return this.lookupMultipartResolver();
   }

   protected MultipartResolver lookupMultipartResolver() {
      WebApplicationContext wac = WebApplicationContextUtils.getWebApplicationContext(this.getServletContext());
      String beanName = this.getMultipartResolverBeanName();
      if (wac != null && wac.containsBean(beanName)) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Using MultipartResolver '" + beanName + "' for MultipartFilter");
         }

         return (MultipartResolver)wac.getBean(beanName, MultipartResolver.class);
      } else {
         return this.defaultMultipartResolver;
      }
   }
}
