package org.springframework.http.server.reactive;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.util.Assert;
import reactor.netty.http.server.HttpServerRequest;

abstract class ReactorUriHelper {
   public static URI createUri(HttpServerRequest request) throws URISyntaxException {
      Assert.notNull(request, "HttpServerRequest must not be null");
      StringBuilder builder = new StringBuilder();
      String scheme = request.scheme();
      builder.append(scheme);
      builder.append("://");
      appendHostName(request, builder);
      int port = request.hostPort();
      if ((scheme.equals("http") || scheme.equals("ws")) && port != 80 || (scheme.equals("https") || scheme.equals("wss")) && port != 443) {
         builder.append(':');
         builder.append(port);
      }

      String prefix = request.forwardedPrefix();
      if (prefix != null && !prefix.isEmpty()) {
         builder.append(prefix);
      }

      appendRequestUri(request, builder);
      return new URI(builder.toString());
   }

   private static void appendHostName(HttpServerRequest request, StringBuilder builder) {
      String hostName = request.hostName();
      boolean ipv6 = hostName.indexOf(58) != -1;
      boolean brackets = ipv6 && !hostName.startsWith("[") && !hostName.endsWith("]");
      if (brackets) {
         builder.append('[');
      }

      if (encoded(hostName, ipv6)) {
         builder.append(hostName);
      } else {
         for (int i = 0; i < hostName.length(); i++) {
            char c = hostName.charAt(i);
            if (isAllowedInHost(c, ipv6)) {
               builder.append(c);
            } else {
               builder.append('%');
               char hex1 = Character.toUpperCase(Character.forDigit(c >> 4 & 15, 16));
               char hex2 = Character.toUpperCase(Character.forDigit(c & 15, 16));
               builder.append(hex1);
               builder.append(hex2);
            }
         }
      }

      if (brackets) {
         builder.append(']');
      }
   }

   private static boolean encoded(String hostName, boolean ipv6) {
      int length = hostName.length();

      for (int i = 0; i < length; i++) {
         char c = hostName.charAt(i);
         if (c == '%') {
            if (i + 2 >= length) {
               return false;
            }

            char hex1 = hostName.charAt(i + 1);
            char hex2 = hostName.charAt(i + 2);
            int u = Character.digit(hex1, 16);
            int l = Character.digit(hex2, 16);
            if (u == -1 || l == -1) {
               return false;
            }

            i += 2;
         } else if (!isAllowedInHost(c, ipv6)) {
            return false;
         }
      }

      return true;
   }

   private static boolean isAllowedInHost(char c, boolean ipv6) {
      return c >= 'a' && c <= 'z'
         || c >= 'A' && c <= 'Z'
         || c >= '0' && c <= '9'
         || '-' == c
         || '.' == c
         || '_' == c
         || '~' == c
         || '!' == c
         || '$' == c
         || '&' == c
         || '\'' == c
         || '(' == c
         || ')' == c
         || '*' == c
         || '+' == c
         || ',' == c
         || ';' == c
         || '=' == c
         || ipv6 && ('[' == c || ']' == c || ':' == c);
   }

   private static void appendRequestUri(HttpServerRequest request, StringBuilder builder) {
      String uri = request.uri();
      int length = uri.length();

      for (int i = 0; i < length; i++) {
         char c = uri.charAt(i);
         if (c == '/' || c == '?' || c == '#') {
            break;
         }

         if (hasAuthority(uri, c, i, length)) {
            for (int var6 = i + 3; var6 < length; var6++) {
               c = uri.charAt(var6);
               if (c == '/' || c == '?' || c == '#') {
                  builder.append(uri, var6, length);
                  return;
               }
            }

            return;
         }
      }

      if (length > 0 && uri.charAt(0) != '/') {
         builder.append('/');
      }

      builder.append(uri);
   }

   private static boolean hasAuthority(String uri, char c, int i, int length) {
      return c == ':' && i + 2 < length && uri.charAt(i + 1) == '/' && uri.charAt(i + 2) == '/';
   }
}
