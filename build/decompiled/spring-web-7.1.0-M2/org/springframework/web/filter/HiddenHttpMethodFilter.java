package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class HiddenHttpMethodFilter extends OncePerRequestFilter {
   private static final List<String> ALLOWED_METHODS = List.of(
      HttpMethod.PUT.name(), HttpMethod.DELETE.name(), HttpMethod.PATCH.name(), HttpMethod.QUERY.name()
   );
   public static final String DEFAULT_METHOD_PARAM = "_method";
   private String methodParam = "_method";

   public void setMethodParam(String methodParam) {
      Assert.hasText(methodParam, "'methodParam' must not be empty");
      this.methodParam = methodParam;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      HttpServletRequest requestToUse = request;
      if ("POST".equals(request.getMethod()) && request.getAttribute("jakarta.servlet.error.exception") == null) {
         String paramValue = request.getParameter(this.methodParam);
         if (StringUtils.hasLength(paramValue)) {
            String method = paramValue.toUpperCase(Locale.ROOT);
            if (ALLOWED_METHODS.contains(method)) {
               requestToUse = new HiddenHttpMethodFilter.HttpMethodRequestWrapper(request, method);
            }
         }
      }

      filterChain.doFilter(requestToUse, response);
   }

   private static class HttpMethodRequestWrapper extends HttpServletRequestWrapper {
      private final String method;

      public HttpMethodRequestWrapper(HttpServletRequest request, String method) {
         super(request);
         this.method = method;
      }

      public String getMethod() {
         return this.method;
      }
   }
}
