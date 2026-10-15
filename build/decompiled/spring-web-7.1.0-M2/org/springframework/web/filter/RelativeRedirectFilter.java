package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;

public class RelativeRedirectFilter extends OncePerRequestFilter {
   private HttpStatusCode redirectStatus = HttpStatus.SEE_OTHER;

   public void setRedirectStatus(HttpStatusCode status) {
      Assert.notNull(status, "Property 'redirectStatus' is required");
      Assert.isTrue(status.is3xxRedirection(), () -> "Not a redirect status code: " + status);
      this.redirectStatus = status;
   }

   public HttpStatusCode getRedirectStatus() {
      return this.redirectStatus;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      response = RelativeRedirectResponseWrapper.wrapIfNecessary(response, this.redirectStatus);
      filterChain.doFilter(request, response);
   }
}
