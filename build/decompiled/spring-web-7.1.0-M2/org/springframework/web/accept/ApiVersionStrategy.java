package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

public interface ApiVersionStrategy {
   @Nullable String resolveVersion(HttpServletRequest request);

   Comparable<?> parseVersion(String version);

   void validateVersion(@Nullable Comparable<?> requestVersion, HttpServletRequest request) throws MissingApiVersionException, InvalidApiVersionException;

   @Nullable Comparable<?> getDefaultVersion();

   default @Nullable Comparable<?> resolveParseAndValidateVersion(HttpServletRequest request) {
      String value = this.resolveVersion(request);
      Comparable<?> version;
      if (value == null) {
         version = this.getDefaultVersion();
      } else {
         try {
            version = this.parseVersion(value);
         } catch (Exception ex) {
            throw new InvalidApiVersionException(value, null, ex);
         }
      }

      this.validateVersion(version, request);
      return version;
   }

   void handleDeprecations(Comparable<?> version, Object handler, HttpServletRequest request, HttpServletResponse response);
}
