package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

public class MediaTypeParamApiVersionResolver implements ApiVersionResolver {
   private final MediaType compatibleMediaType;
   private final String parameterName;

   public MediaTypeParamApiVersionResolver(MediaType compatibleMediaType, String paramName) {
      this.compatibleMediaType = compatibleMediaType;
      this.parameterName = paramName;
   }

   @Override
   public @Nullable String resolveVersion(HttpServletRequest request) {
      Enumeration<String> headers = request.getHeaders("Accept");

      while (headers.hasMoreElements()) {
         String header = headers.nextElement();

         for (MediaType mediaType : MediaType.parseMediaTypes(header)) {
            if (this.compatibleMediaType.isCompatibleWith(mediaType)) {
               return mediaType.getParameter(this.parameterName);
            }
         }
      }

      String header = request.getHeader("Content-Type");

      for (MediaType mediaType : MediaType.parseMediaTypes(header)) {
         if (this.compatibleMediaType.isCompatibleWith(mediaType)) {
            return mediaType.getParameter(this.parameterName);
         }
      }

      return null;
   }
}
