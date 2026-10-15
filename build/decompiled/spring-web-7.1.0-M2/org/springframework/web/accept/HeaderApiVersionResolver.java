package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

public class HeaderApiVersionResolver implements ApiVersionResolver {
   private final String headerName;

   public HeaderApiVersionResolver(String headerName) {
      this.headerName = headerName;
   }

   @Override
   public @Nullable String resolveVersion(HttpServletRequest request) {
      return request.getHeader(this.headerName);
   }
}
