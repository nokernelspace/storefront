package org.springframework.web.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;

public class ServletRequestPathFilter implements Filter {
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
      RequestPath previousRequestPath = (RequestPath)request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
      ServletRequestPathUtils.parseAndCache((HttpServletRequest)request);

      try {
         chain.doFilter(request, response);
      } finally {
         ServletRequestPathUtils.setParsedRequestPath(previousRequestPath, request);
      }
   }
}
