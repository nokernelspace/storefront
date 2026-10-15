package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ForwardedHeaderUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UrlPathHelper;

public class ForwardedHeaderFilter extends OncePerRequestFilter {
   private static final Log logger = LogFactory.getLog(ForwardedHeaderFilter.class);
   private static final Set<String> FORWARDED_HEADER_NAMES = Collections.newSetFromMap(new LinkedCaseInsensitiveMap(10, Locale.ROOT));
   private final @Nullable Boolean useStandardHeader;
   private boolean useForwardedPrefix;
   private boolean removeOnly;
   private boolean relativeRedirects;

   @Deprecated(since = "7.1", forRemoval = true)
   public ForwardedHeaderFilter() {
      this.useStandardHeader = null;
      this.useForwardedPrefix = true;
   }

   public ForwardedHeaderFilter(boolean useStandardHeader) {
      this.useStandardHeader = useStandardHeader;
   }

   public void setUseForwardedPrefix(boolean useForwardedPrefix) {
      this.useForwardedPrefix = useForwardedPrefix;
   }

   public void setRemoveOnly(boolean removeOnly) {
      this.removeOnly = removeOnly;
   }

   public void setRelativeRedirects(boolean relativeRedirects) {
      this.relativeRedirects = relativeRedirects;
   }

   @Override
   protected boolean shouldNotFilter(HttpServletRequest request) {
      for (String headerName : FORWARDED_HEADER_NAMES) {
         if (request.getHeader(headerName) != null) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected boolean shouldNotFilterAsyncDispatch() {
      return false;
   }

   @Override
   protected boolean shouldNotFilterErrorDispatch() {
      return false;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      if (this.removeOnly) {
         ForwardedHeaderFilter.ForwardedHeaderRemovingRequest wrappedRequest = new ForwardedHeaderFilter.ForwardedHeaderRemovingRequest(request);
         filterChain.doFilter(wrappedRequest, response);
      } else {
         HttpServletRequest wrappedRequest = null;
         HttpServletResponse wrappedResponse = null;

         try {
            var9 = new ForwardedHeaderFilter.ForwardedHeaderExtractingRequest(request, this.useStandardHeader, this.useForwardedPrefix);
            var10 = this.relativeRedirects
               ? RelativeRedirectResponseWrapper.wrapIfNecessary(response, HttpStatus.SEE_OTHER)
               : new ForwardedHeaderFilter.ForwardedHeaderExtractingResponse(response, var9, this.useStandardHeader);
         } catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Failed to apply forwarded headers to " + this.formatRequest(request), ex);
            }

            response.sendError(400);
            return;
         }

         filterChain.doFilter(var9, (ServletResponse)var10);
      }
   }

   protected String formatRequest(HttpServletRequest request) {
      return "HTTP " + request.getMethod() + " \"" + request.getRequestURI() + "\"";
   }

   @Override
   protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
      this.doFilterInternal(request, response, filterChain);
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

   static {
      FORWARDED_HEADER_NAMES.add("Forwarded");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Proto");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Ssl");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Host");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Port");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-For");
      FORWARDED_HEADER_NAMES.add("X-Forwarded-Prefix");
   }

   private static class ErrorPathRequest extends HttpServletRequestWrapper {
      ErrorPathRequest(ServletRequest request) {
         super((HttpServletRequest)request);
      }

      public String getRequestURI() {
         String requestUri = (String)this.getAttribute("jakarta.servlet.error.request_uri");
         Assert.isTrue(requestUri != null, "Expected ERROR requestUri attribute");
         return requestUri;
      }
   }

   private static class ForwardedHeaderExtractingRequest extends ForwardedHeaderFilter.ForwardedHeaderRemovingRequest {
      private final @Nullable String scheme;
      private final boolean secure;
      private final @Nullable String host;
      private final int port;
      private final @Nullable InetSocketAddress remoteAddress;
      private final @Nullable InetSocketAddress localAddress;
      private final ForwardedHeaderFilter.ForwardedPrefixExtractor forwardedPrefixExtractor;

      ForwardedHeaderExtractingRequest(HttpServletRequest servletRequest, @Nullable Boolean useStandardHeader, boolean useForwardedPrefix) {
         super(servletRequest);
         ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
         ForwardedHeaderUtils.ForwardedInfo info = ForwardedHeaderFilter.getForwardedInfo(
            useStandardHeader, request.getURI(), request.getHeaders(), request.getRemoteAddress(), request.getLocalAddress()
         );
         UriComponents uriComponents = info.uriComponentsBuilder().build();
         int port = uriComponents.getPort();
         this.scheme = uriComponents.getScheme();
         this.secure = "https".equals(this.scheme) || "wss".equals(this.scheme);
         this.host = uriComponents.getHost();
         this.port = port == -1 ? (this.secure ? 443 : 80) : port;
         this.remoteAddress = info.forAddress();
         this.localAddress = info.byAddress();
         Supplier<HttpServletRequest> requestSupplier = () -> (HttpServletRequest)this.getRequest();
         this.forwardedPrefixExtractor = new ForwardedHeaderFilter.ForwardedPrefixExtractor(
            requestSupplier, this.scheme + "://" + this.host + (port == -1 ? "" : ":" + port), useForwardedPrefix
         );
      }

      public @Nullable String getScheme() {
         return this.scheme;
      }

      public @Nullable String getServerName() {
         return this.host;
      }

      public int getServerPort() {
         return this.port;
      }

      public boolean isSecure() {
         return this.secure;
      }

      public String getContextPath() {
         return this.forwardedPrefixExtractor.getContextPath();
      }

      public String getRequestURI() {
         return this.forwardedPrefixExtractor.getRequestUri();
      }

      public StringBuffer getRequestURL() {
         return this.forwardedPrefixExtractor.getRequestUrl();
      }

      public @Nullable String getRemoteHost() {
         return this.remoteAddress != null ? this.remoteAddress.getHostString() : super.getRemoteHost();
      }

      public @Nullable String getRemoteAddr() {
         return this.remoteAddress != null ? this.remoteAddress.getHostString() : super.getRemoteAddr();
      }

      public int getRemotePort() {
         return this.remoteAddress != null ? this.remoteAddress.getPort() : super.getRemotePort();
      }

      public @Nullable String getLocalAddr() {
         return this.localAddress != null ? this.localAddress.getHostString() : super.getLocalAddr();
      }

      public int getLocalPort() {
         return this.localAddress != null ? this.localAddress.getPort() : super.getLocalPort();
      }

      public @Nullable Object getAttribute(String name) {
         return name.equals("jakarta.servlet.error.request_uri") ? this.forwardedPrefixExtractor.getErrorRequestUri() : super.getAttribute(name);
      }
   }

   private static class ForwardedHeaderExtractingResponse extends HttpServletResponseWrapper {
      private static final String FOLDER_SEPARATOR = "/";
      private final HttpServletRequest request;
      private final @Nullable Boolean useStandardHeader;

      ForwardedHeaderExtractingResponse(HttpServletResponse response, HttpServletRequest request, @Nullable Boolean useStandardHeader) {
         super(response);
         this.request = request;
         this.useStandardHeader = useStandardHeader;
      }

      public void sendRedirect(String location) throws IOException {
         UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(location);
         UriComponents uriComponents = builder.build();
         if (uriComponents.getScheme() != null) {
            super.sendRedirect(location);
         } else if (location.startsWith("//")) {
            String scheme = this.request.getScheme();
            super.sendRedirect(builder.scheme(scheme).toUriString());
         } else {
            String path = uriComponents.getPath();
            if (path != null) {
               path = path.startsWith("/") ? path : StringUtils.applyRelativePath(this.request.getRequestURI(), path);
            }

            ServletServerHttpRequest httpRequest = new ServletServerHttpRequest(this.request);
            URI uri = httpRequest.getURI();
            HttpHeaders headers = httpRequest.getHeaders();
            ForwardedHeaderUtils.ForwardedInfo info = ForwardedHeaderFilter.getForwardedInfo(this.useStandardHeader, uri, headers, null, null);
            String result = info.uriComponentsBuilder()
               .replacePath(path)
               .replaceQuery(uriComponents.getQuery())
               .fragment(uriComponents.getFragment())
               .build()
               .normalize()
               .toUriString();
            super.sendRedirect(result);
         }
      }
   }

   private static class ForwardedHeaderRemovingRequest extends HttpServletRequestWrapper {
      private final Set<String> headerNames;

      public ForwardedHeaderRemovingRequest(HttpServletRequest request) {
         super(request);
         this.headerNames = headerNames(request);
      }

      private static Set<String> headerNames(HttpServletRequest request) {
         Set<String> headerNames = Collections.newSetFromMap(new LinkedCaseInsensitiveMap(Locale.ROOT));
         Enumeration<String> names = request.getHeaderNames();

         while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!ForwardedHeaderFilter.FORWARDED_HEADER_NAMES.contains(name)) {
               headerNames.add(name);
            }
         }

         return Collections.unmodifiableSet(headerNames);
      }

      public @Nullable String getHeader(String name) {
         return ForwardedHeaderFilter.FORWARDED_HEADER_NAMES.contains(name) ? null : super.getHeader(name);
      }

      public Enumeration<String> getHeaders(String name) {
         return ForwardedHeaderFilter.FORWARDED_HEADER_NAMES.contains(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
      }

      public Enumeration<String> getHeaderNames() {
         return Collections.enumeration(this.headerNames);
      }
   }

   private static class ForwardedPrefixExtractor {
      private final Supplier<HttpServletRequest> delegate;
      private final String baseUrl;
      private String actualRequestUri;
      private final @Nullable String forwardedPrefix;
      private @Nullable String requestUri;
      private String requestUrl;

      public ForwardedPrefixExtractor(Supplier<HttpServletRequest> delegate, String baseUrl, boolean useForwardedPrefix) {
         this.delegate = delegate;
         this.baseUrl = baseUrl;
         this.actualRequestUri = delegate.get().getRequestURI();
         this.forwardedPrefix = useForwardedPrefix ? initForwardedPrefix(delegate.get()) : null;
         this.requestUri = this.initRequestUri();
         this.requestUrl = this.initRequestUrl();
      }

      private static @Nullable String initForwardedPrefix(HttpServletRequest request) {
         String result = null;
         Enumeration<String> names = request.getHeaderNames();

         while (names.hasMoreElements()) {
            String name = names.nextElement();
            if ("X-Forwarded-Prefix".equalsIgnoreCase(name)) {
               result = request.getHeader(name);
            }
         }

         if (result == null) {
            return null;
         }

         StringBuilder prefix = new StringBuilder(result.length());
         String[] rawPrefixes = StringUtils.tokenizeToStringArray(result, ",");

         for (String rawPrefix : rawPrefixes) {
            int endIndex = rawPrefix.length();

            while (endIndex > 0 && rawPrefix.charAt(endIndex - 1) == '/') {
               endIndex--;
            }

            prefix.append(endIndex != rawPrefix.length() ? rawPrefix.substring(0, endIndex) : rawPrefix);
         }

         return prefix.toString();
      }

      private @Nullable String initRequestUri() {
         return this.forwardedPrefix != null ? this.forwardedPrefix + UrlPathHelper.rawPathInstance.getPathWithinApplication(this.delegate.get()) : null;
      }

      private String initRequestUrl() {
         return this.baseUrl + (this.requestUri != null ? this.requestUri : this.delegate.get().getRequestURI());
      }

      public String getContextPath() {
         return this.forwardedPrefix != null ? this.forwardedPrefix : this.delegate.get().getContextPath();
      }

      public String getRequestUri() {
         if (this.requestUri == null) {
            return this.delegate.get().getRequestURI();
         }

         this.recalculatePathsIfNecessary();
         return this.requestUri;
      }

      public StringBuffer getRequestUrl() {
         this.recalculatePathsIfNecessary();
         return new StringBuffer(this.requestUrl);
      }

      private void recalculatePathsIfNecessary() {
         if (!this.actualRequestUri.equals(this.delegate.get().getRequestURI())) {
            this.actualRequestUri = this.delegate.get().getRequestURI();
            this.requestUri = this.initRequestUri();
            this.requestUrl = this.initRequestUrl();
         }
      }

      public @Nullable String getErrorRequestUri() {
         HttpServletRequest request = this.delegate.get();
         String requestUri = (String)request.getAttribute("jakarta.servlet.error.request_uri");
         if (this.forwardedPrefix != null && requestUri != null) {
            ForwardedHeaderFilter.ErrorPathRequest errorRequest = new ForwardedHeaderFilter.ErrorPathRequest(request);
            return this.forwardedPrefix + UrlPathHelper.rawPathInstance.getPathWithinApplication(errorRequest);
         } else {
            return requestUri;
         }
      }
   }
}
