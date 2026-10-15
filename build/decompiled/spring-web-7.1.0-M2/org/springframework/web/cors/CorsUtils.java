package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public abstract class CorsUtils {
   public static boolean isCorsRequest(HttpServletRequest request) {
      String origin = request.getHeader("Origin");
      if (origin == null) {
         return false;
      }

      UriComponents originUrl = UriComponentsBuilder.fromUriString(origin).build();
      String scheme = request.getScheme();
      String host = request.getServerName();
      int port = request.getServerPort();
      return !ObjectUtils.nullSafeEquals(scheme, originUrl.getScheme())
         || !ObjectUtils.nullSafeEquals(host, originUrl.getHost())
         || getPort(scheme, port) != getPort(originUrl.getScheme(), originUrl.getPort());
   }

   private static int getPort(@Nullable String scheme, int port) {
      if (port == -1) {
         if ("http".equals(scheme) || "ws".equals(scheme)) {
            port = 80;
         } else if ("https".equals(scheme) || "wss".equals(scheme)) {
            port = 443;
         }
      }

      return port;
   }

   public static boolean isPreFlightRequest(HttpServletRequest request) {
      return HttpMethod.OPTIONS.matches(request.getMethod())
         && request.getHeader("Origin") != null
         && request.getHeader("Access-Control-Request-Method") != null;
   }
}
