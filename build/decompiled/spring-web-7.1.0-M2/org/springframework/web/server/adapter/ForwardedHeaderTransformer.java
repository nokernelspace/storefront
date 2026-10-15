package org.springframework.web.server.adapter;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ForwardedHeaderUtils;

public class ForwardedHeaderTransformer implements Function<ServerHttpRequest, ServerHttpRequest> {
   static final Set<String> FORWARDED_HEADER_NAMES = Collections.newSetFromMap(new LinkedCaseInsensitiveMap(10, Locale.ROOT));
   private final @Nullable Boolean useStandardHeader;
   private boolean useForwardedPrefix;
   private boolean removeOnly;

   @Deprecated(since = "7.1", forRemoval = true)
   public ForwardedHeaderTransformer() {
      this.useStandardHeader = null;
      this.useForwardedPrefix = true;
   }

   public ForwardedHeaderTransformer(boolean useStandardHeader) {
      this.useStandardHeader = useStandardHeader;
   }

   public void setUseForwardedPrefix(boolean useForwardedPrefix) {
      this.useForwardedPrefix = useForwardedPrefix;
   }

   public void setRemoveOnly(boolean removeOnly) {
      this.removeOnly = removeOnly;
   }

   public boolean isRemoveOnly() {
      return this.removeOnly;
   }

   public ServerHttpRequest apply(ServerHttpRequest request) {
      if (!this.hasForwardedHeaders(request)) {
         return request;
      }

      ServerHttpRequest.Builder builder = request.mutate();
      if (!this.removeOnly) {
         URI originalUri = request.getURI();
         HttpHeaders headers = request.getHeaders();
         InetSocketAddress remoteAddress = request.getRemoteAddress();
         InetSocketAddress localAddress = request.getLocalAddress();
         ForwardedHeaderUtils.ForwardedInfo info = getForwardedInfo(this.useStandardHeader, originalUri, headers, remoteAddress, localAddress);
         URI uri = info.uri();
         builder.uri(uri);
         if (this.useForwardedPrefix) {
            String prefix = getForwardedPrefix(request);
            if (prefix != null) {
               builder.path(prefix + uri.getRawPath());
               builder.contextPath(prefix);
            }
         }

         remoteAddress = info.forAddress();
         if (remoteAddress != null) {
            builder.remoteAddress(remoteAddress);
         }

         localAddress = info.byAddress();
         if (localAddress != null) {
            builder.localAddress(localAddress);
         }
      }

      this.removeForwardedHeaders(builder);
      return builder.build();
   }

   protected boolean hasForwardedHeaders(ServerHttpRequest request) {
      for (String name : FORWARDED_HEADER_NAMES) {
         if (request.getHeaders().containsHeader(name)) {
            return true;
         }
      }

      return false;
   }

   private static ForwardedHeaderUtils.ForwardedInfo getForwardedInfo(
      @Nullable Boolean useStandardHeader, URI uri, HttpHeaders headers, @Nullable InetSocketAddress remoteAddress, @Nullable InetSocketAddress localAddress
   ) {
      if (useStandardHeader == null) {
         return new ForwardedHeaderUtils.ForwardedInfo(
            ForwardedHeaderUtils.adaptFromForwardedHeaders(uri, headers),
            ForwardedHeaderUtils.parseForwardedFor(uri, headers, remoteAddress),
            ForwardedHeaderUtils.parseForwardedBy(uri, headers, localAddress)
         );
      } else {
         return useStandardHeader
            ? ForwardedHeaderUtils.parseStandardHeader(uri, headers, remoteAddress, localAddress)
            : ForwardedHeaderUtils.parseXForwardedHeaders(uri, headers, remoteAddress, localAddress);
      }
   }

   private void removeForwardedHeaders(ServerHttpRequest.Builder builder) {
      builder.headers(map -> FORWARDED_HEADER_NAMES.forEach(map::remove));
   }

   private static @Nullable String getForwardedPrefix(ServerHttpRequest request) {
      HttpHeaders headers = request.getHeaders();
      String header = headers.getFirst("X-Forwarded-Prefix");
      if (header == null) {
         return null;
      }

      StringBuilder prefix = new StringBuilder(header.length());
      String[] rawPrefixes = StringUtils.tokenizeToStringArray(header, ",");

      for (String rawPrefix : rawPrefixes) {
         int endIndex = rawPrefix.length();

         while (endIndex > 0 && rawPrefix.charAt(endIndex - 1) == '/') {
            endIndex--;
         }

         prefix.append(endIndex != rawPrefix.length() ? rawPrefix.substring(0, endIndex) : rawPrefix);
      }

      return prefix.toString();
   }

   static {
      FORWARDED_HEADER_NAMES.add("Forwarded");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Proto");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Ssl");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Host");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Port");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-For");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Prefix");
   }
}
