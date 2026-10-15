package org.springframework.web.filter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.web.util.WebUtils;

final class RelativeRedirectResponseWrapper extends HttpServletResponseWrapper {
   private final HttpStatusCode redirectStatus;

   private RelativeRedirectResponseWrapper(HttpServletResponse response, HttpStatusCode redirectStatus) {
      super(response);
      Assert.notNull(redirectStatus, "'redirectStatus' is required");
      this.redirectStatus = redirectStatus;
   }

   public void sendRedirect(String location) throws IOException {
      this.resetBuffer();
      this.setStatus(this.redirectStatus.value());
      this.setHeader("Location", location);
      this.flushBuffer();
   }

   public static HttpServletResponse wrapIfNecessary(HttpServletResponse response, HttpStatusCode redirectStatus) {
      RelativeRedirectResponseWrapper wrapper = WebUtils.getNativeResponse(response, RelativeRedirectResponseWrapper.class);
      return (HttpServletResponse)(wrapper != null ? response : new RelativeRedirectResponseWrapper(response, redirectStatus));
   }
}
