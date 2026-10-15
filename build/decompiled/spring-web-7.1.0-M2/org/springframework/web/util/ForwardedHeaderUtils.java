package org.springframework.web.util;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public abstract class ForwardedHeaderUtils {
   private static final String FORWARDED_VALUE = "\"?([^;,\"]+)\"?";
   private static final Pattern FORWARDED_HOST_PATTERN = Pattern.compile("(?i:host)=\"?([^;,\"]+)\"?");
   private static final Pattern FORWARDED_PROTO_PATTERN = Pattern.compile("(?i:proto)=\"?([^;,\"]+)\"?");
   private static final Pattern FORWARDED_FOR_PATTERN = Pattern.compile("(?i:for)=\"?([^;,\"]+)\"?");
   private static final Pattern FORWARDED_BY_PATTERN = Pattern.compile("(?i:by)=\"?([^;,\"]+)\"?");

   public static ForwardedHeaderUtils.ForwardedInfo parseStandardHeader(
      URI uri, HttpHeaders headers, @Nullable InetSocketAddress remoteAddress, @Nullable InetSocketAddress localAddress
   ) {
      UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(uri);
      InetSocketAddress forAddress = null;
      InetSocketAddress byAddress = null;
      String forwardedHeader = headers.getFirst("Forwarded");
      if (StringUtils.hasText(forwardedHeader)) {
         Map<String, String> pairs = parseFirstElement(forwardedHeader);
         String proto = pairs.get("proto");
         if (proto != null) {
            uriComponentsBuilder.scheme(proto);
            uriComponentsBuilder.port(null);
         }

         String host = pairs.get("host");
         if (host != null) {
            adaptForwardedHost(uriComponentsBuilder, host);
         }

         uriComponentsBuilder.resetPortIfDefaultForScheme();
         String forValue = pairs.get("for");
         if (forValue != null) {
            forAddress = parseInetSocketAddress(forValue, getPortToUse(remoteAddress, uri));
         }

         String byValue = pairs.get("by");
         if (byValue != null) {
            byAddress = parseInetSocketAddress(byValue, getPortToUse(localAddress, uri));
         }
      }

      return new ForwardedHeaderUtils.ForwardedInfo(uriComponentsBuilder, forAddress, byAddress);
   }

   private static Map<String, String> parseFirstElement(String header) {
      Map<String, String> pairs = new LinkedHashMap<>(4);
      int index = 0;
      int length = header.length();

      while (index < length) {
         index = skipWhiteSpaces(header, index);
         if (index == length || header.charAt(index) == ',') {
            break;
         }

         if (header.charAt(index) == ';') {
            index++;
         } else {
            int nameStart = index;

            while (index < length && isTokenChar(header.charAt(index))) {
               index++;
            }

            String name = header.substring(nameStart, index);
            index = skipWhiteSpaces(header, index);
            if (!name.isEmpty() && index < length && header.charAt(index) == '=') {
               index = skipWhiteSpaces(header, index + 1);
               String value;
               if (index < length && header.charAt(index) == '"') {
                  StringBuilder sb = new StringBuilder();
                  index++;

                  while (index < length) {
                     char c = header.charAt(index++);
                     if (c == '"') {
                        break;
                     }

                     if (c == '\\' && index < length) {
                        c = header.charAt(index++);
                     }

                     sb.append(c);
                  }

                  value = sb.toString();
               } else {
                  int valueStart = index;
                  index = skipToDelimiter(header, index);
                  value = header.substring(valueStart, index);
               }

               pairs.putIfAbsent(name.toLowerCase(Locale.ROOT), value.trim());
            }

            index = skipToDelimiter(header, index);
            if (index < length && header.charAt(index) == ',') {
               break;
            }

            index++;
         }
      }

      return pairs;
   }

   private static int skipWhiteSpaces(String header, int index) {
      while (index < header.length() && (header.charAt(index) == ' ' || header.charAt(index) == '\t')) {
         index++;
      }

      return index;
   }

   private static int skipToDelimiter(String header, int index) {
      while (index < header.length() && header.charAt(index) != ';' && header.charAt(index) != ',') {
         index++;
      }

      return index;
   }

   private static boolean isTokenChar(char c) {
      return c >= 'a' && c <= 'z'
         || c >= 'A' && c <= 'Z'
         || c >= '0' && c <= '9'
         || c == '!'
         || c == '#'
         || c == '$'
         || c == '%'
         || c == '&'
         || c == '\''
         || c == '*'
         || c == '+'
         || c == '-'
         || c == '.'
         || c == '^'
         || c == '_'
         || c == '`'
         || c == '|'
         || c == '~';
   }

   private static void adaptForwardedHost(UriComponentsBuilder uriComponentsBuilder, String rawValue) {
      int portSeparatorIdx = rawValue.lastIndexOf(58);
      int squareBracketIdx = rawValue.lastIndexOf(93);
      if (portSeparatorIdx > squareBracketIdx) {
         if (squareBracketIdx == -1 && rawValue.indexOf(58) != portSeparatorIdx) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + rawValue);
         }

         uriComponentsBuilder.host(rawValue.substring(0, portSeparatorIdx));

         try {
            uriComponentsBuilder.port(Integer.parseInt(rawValue, portSeparatorIdx + 1, rawValue.length(), 10));
         } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Failed to parse port in forwarded host value: " + rawValue + "\"");
         }
      } else {
         uriComponentsBuilder.host(rawValue);
         uriComponentsBuilder.port(null);
      }
   }

   private static int getPortToUse(@Nullable InetSocketAddress address, URI uri) {
      return address != null ? address.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
   }

   private static InetSocketAddress parseInetSocketAddress(String value, int port) {
      String host = value;
      int portSeparatorIdx = value.lastIndexOf(58);
      int squareBracketIdx = value.lastIndexOf(93);
      if (portSeparatorIdx <= squareBracketIdx) {
         return InetSocketAddress.createUnresolved(host, port);
      }

      if (squareBracketIdx == -1 && value.indexOf(58) != portSeparatorIdx) {
         throw new IllegalArgumentException("Invalid IPv4 address: " + value);
      }

      host = value.substring(0, portSeparatorIdx);

      try {
         port = Integer.parseInt(value, portSeparatorIdx + 1, value.length(), 10);
         return InetSocketAddress.createUnresolved(host, port);
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException("Failed to parse port in forwarded address value: " + value);
      }
   }

   public static ForwardedHeaderUtils.ForwardedInfo parseXForwardedHeaders(
      URI uri, HttpHeaders headers, @Nullable InetSocketAddress remoteAddress, @Nullable InetSocketAddress localAddress
   ) {
      UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(uri);
      String protocolHeader = headers.getFirst("X-Forwarded-Proto");
      if (StringUtils.hasText(protocolHeader)) {
         uriComponentsBuilder.scheme(getLeftMostValue(protocolHeader));
         uriComponentsBuilder.port(null);
      } else if (isForwardedSslOn(headers)) {
         uriComponentsBuilder.scheme("https");
         uriComponentsBuilder.port(null);
      }

      String hostHeader = headers.getFirst("X-Forwarded-Host");
      if (StringUtils.hasText(hostHeader)) {
         adaptForwardedHost(uriComponentsBuilder, getLeftMostValue(hostHeader));
      }

      String portHeader = headers.getFirst("X-Forwarded-Port");
      if (StringUtils.hasText(portHeader)) {
         try {
            uriComponentsBuilder.port(Integer.parseInt(getLeftMostValue(portHeader)));
         } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Failed to parse \"X-Forwarded-Port: " + portHeader + "\"");
         }
      }

      uriComponentsBuilder.resetPortIfDefaultForScheme();
      InetSocketAddress forAddress = null;
      String forHeader = headers.getFirst("X-Forwarded-For");
      if (StringUtils.hasText(forHeader)) {
         String host = getLeftMostValue(forHeader);
         boolean ipv6 = host.indexOf(58) != -1;
         host = ipv6 && !host.startsWith("[") && !host.endsWith("]") ? "[" + host + "]" : host;
         int port = getPortToUse(remoteAddress, uri);
         forAddress = InetSocketAddress.createUnresolved(host, port);
      }

      return new ForwardedHeaderUtils.ForwardedInfo(uriComponentsBuilder, forAddress, null);
   }

   private static String getLeftMostValue(String headerValue) {
      return StringUtils.tokenizeToStringArray(headerValue, ",")[0];
   }

   private static boolean isForwardedSslOn(HttpHeaders headers) {
      String forwardedSsl = headers.getFirst("X-Forwarded-Ssl");
      return StringUtils.hasText(forwardedSsl) && forwardedSsl.equalsIgnoreCase("on");
   }

   @Deprecated(since = "7.1", forRemoval = true)
   public static UriComponentsBuilder adaptFromForwardedHeaders(URI uri, HttpHeaders headers) {
      UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(uri);

      try {
         String forwardedHeader = headers.getFirst("Forwarded");
         if (StringUtils.hasText(forwardedHeader)) {
            Matcher matcher = FORWARDED_PROTO_PATTERN.matcher(forwardedHeader);
            if (matcher.find()) {
               uriComponentsBuilder.scheme(matcher.group(1).trim());
               uriComponentsBuilder.port(null);
            } else if (isForwardedSslOn(headers)) {
               uriComponentsBuilder.scheme("https");
               uriComponentsBuilder.port(null);
            }

            matcher = FORWARDED_HOST_PATTERN.matcher(forwardedHeader);
            if (matcher.find()) {
               adaptForwardedHost(uriComponentsBuilder, matcher.group(1).trim());
            }
         } else {
            String protocolHeader = headers.getFirst("X-Forwarded-Proto");
            if (StringUtils.hasText(protocolHeader)) {
               uriComponentsBuilder.scheme(getLeftMostValue(protocolHeader));
               uriComponentsBuilder.port(null);
            } else if (isForwardedSslOn(headers)) {
               uriComponentsBuilder.scheme("https");
               uriComponentsBuilder.port(null);
            }

            String hostHeader = headers.getFirst("X-Forwarded-Host");
            if (StringUtils.hasText(hostHeader)) {
               adaptForwardedHost(uriComponentsBuilder, getLeftMostValue(hostHeader));
            }

            String portHeader = headers.getFirst("X-Forwarded-Port");
            if (StringUtils.hasText(portHeader)) {
               uriComponentsBuilder.port(Integer.parseInt(getLeftMostValue(portHeader)));
            }
         }
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException(
            "Failed to parse a port from \"forwarded\"-type headers. If not behind a trusted proxy, consider using ForwardedHeaderFilter with removeOnly=true. Request headers: "
               + headers
         );
      }

      uriComponentsBuilder.resetPortIfDefaultForScheme();
      return uriComponentsBuilder;
   }

   @Deprecated(since = "7.1", forRemoval = true)
   public static @Nullable InetSocketAddress parseForwardedFor(URI uri, HttpHeaders headers, @Nullable InetSocketAddress remoteAddress) {
      String forwardedHeader = headers.getFirst("Forwarded");
      if (StringUtils.hasText(forwardedHeader)) {
         String forwardedToUse = getLeftMostValue(forwardedHeader);
         Matcher matcher = FORWARDED_FOR_PATTERN.matcher(forwardedToUse);
         if (matcher.find()) {
            String value = matcher.group(1).trim();
            return parseInetSocketAddress(value, getPortToUse(remoteAddress, uri));
         }
      }

      String forHeader = headers.getFirst("X-Forwarded-For");
      if (StringUtils.hasText(forHeader)) {
         String host = getLeftMostValue(forHeader);
         boolean ipv6 = host.indexOf(58) != -1;
         host = ipv6 && !host.startsWith("[") && !host.endsWith("]") ? "[" + host + "]" : host;
         return InetSocketAddress.createUnresolved(host, getPortToUse(remoteAddress, uri));
      } else {
         return null;
      }
   }

   @Deprecated(since = "7.1", forRemoval = true)
   public static @Nullable InetSocketAddress parseForwardedBy(URI uri, HttpHeaders headers, @Nullable InetSocketAddress localAddress) {
      String forwardedHeader = headers.getFirst("Forwarded");
      if (StringUtils.hasText(forwardedHeader)) {
         String forwardedToUse = getLeftMostValue(forwardedHeader);
         Matcher matcher = FORWARDED_BY_PATTERN.matcher(forwardedToUse);
         if (matcher.find()) {
            String value = matcher.group(1).trim();
            return parseInetSocketAddress(value, getPortToUse(localAddress, uri));
         }
      }

      return null;
   }

   public record ForwardedInfo(UriComponentsBuilder uriComponentsBuilder, @Nullable InetSocketAddress forAddress, @Nullable InetSocketAddress byAddress) {
      public URI uri() {
         UriComponents components = this.uriComponentsBuilder().build();

         try {
            return new URI(components.toUriString());
         } catch (URISyntaxException ex) {
            throw new IllegalStateException("Could not create URI object: " + ex.getMessage(), ex);
         }
      }
   }
}
