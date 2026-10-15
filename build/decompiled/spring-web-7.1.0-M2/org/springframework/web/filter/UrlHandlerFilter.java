package org.springframework.web.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class UrlHandlerFilter extends OncePerRequestFilter {
   private static final Log logger = LogFactory.getLog(UrlHandlerFilter.class);
   private final MultiValueMap<UrlHandlerFilter.Handler, PathPattern> handlers;

   private UrlHandlerFilter(MultiValueMap<UrlHandlerFilter.Handler, PathPattern> handlers) {
      this.handlers = new LinkedMultiValueMap(handlers);
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
      RequestPath path = ServletRequestPathUtils.hasParsedRequestPath(request)
         ? ServletRequestPathUtils.getParsedRequestPath(request)
         : ServletRequestPathUtils.parse(request);

      for (Entry<UrlHandlerFilter.Handler, List<PathPattern>> entry : this.handlers.entrySet()) {
         if (entry.getKey().supports(request, path)) {
            for (PathPattern pattern : entry.getValue()) {
               if (pattern.matches(path)) {
                  entry.getKey().handle(request, response, chain);
                  return;
               }
            }
         }
      }

      chain.doFilter(request, response);
   }

   public static UrlHandlerFilter.Builder.TrailingSlashSpec trailingSlashHandler(String... pathPatterns) {
      return new UrlHandlerFilter.DefaultBuilder().trailingSlashHandler(pathPatterns);
   }

   private abstract static class AbstractTrailingSlashHandler implements UrlHandlerFilter.Handler {
      private static final Consumer<HttpServletRequest> defaultInterceptor = request -> {
         if (UrlHandlerFilter.logger.isTraceEnabled()) {
            UrlHandlerFilter.logger.trace("Handling trailing slash URL: " + request.getMethod() + " " + request.getRequestURI());
         }
      };
      private final Consumer<HttpServletRequest> interceptor;

      protected AbstractTrailingSlashHandler(@Nullable Consumer<HttpServletRequest> interceptor) {
         this.interceptor = interceptor != null ? interceptor : defaultInterceptor;
      }

      @Override
      public boolean supports(HttpServletRequest request, RequestPath path) {
         List<PathContainer.Element> elements = path.pathWithinApplication().elements();
         return elements.size() > 1 && elements.get(elements.size() - 1).value().equals("/");
      }

      @Override
      public void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
         this.interceptor.accept(request);
         this.handleInternal(request, response, chain);
      }

      protected abstract void handleInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException;

      protected String trimTrailingSlash(String path) {
         int index = StringUtils.hasLength(path) ? path.lastIndexOf(47) : -1;
         return index != -1 ? path.substring(0, index) : path;
      }
   }

   public interface Builder {
      UrlHandlerFilter.Builder.TrailingSlashSpec trailingSlashHandler(String... pathPatterns);

      UrlHandlerFilter build();

      interface TrailingSlashSpec {
         UrlHandlerFilter.Builder.TrailingSlashSpec intercept(Consumer<HttpServletRequest> consumer);

         UrlHandlerFilter.Builder redirect(HttpStatus status);

         UrlHandlerFilter.Builder wrapRequest();
      }
   }

   private static final class DefaultBuilder implements UrlHandlerFilter.Builder {
      private final PathPatternParser patternParser = new PathPatternParser();
      private final MultiValueMap<UrlHandlerFilter.Handler, PathPattern> handlers = new LinkedMultiValueMap();

      @Override
      public UrlHandlerFilter.Builder.TrailingSlashSpec trailingSlashHandler(String... patterns) {
         return new UrlHandlerFilter.DefaultBuilder.DefaultTrailingSlashSpec(patterns);
      }

      private UrlHandlerFilter.DefaultBuilder addHandler(List<PathPattern> pathPatterns, UrlHandlerFilter.Handler handler) {
         pathPatterns.forEach(pattern -> this.handlers.add(handler, pattern));
         return this;
      }

      @Override
      public UrlHandlerFilter build() {
         return new UrlHandlerFilter(this.handlers);
      }

      private final class DefaultTrailingSlashSpec implements UrlHandlerFilter.Builder.TrailingSlashSpec {
         private final List<PathPattern> pathPatterns;
         private @Nullable Consumer<HttpServletRequest> interceptor;

         private DefaultTrailingSlashSpec(String[] patterns) {
            this.pathPatterns = Arrays.stream(patterns)
               .map(pattern -> !pattern.endsWith("**") && !pattern.endsWith("/") ? pattern + "/" : pattern)
               .map(DefaultBuilder.this.patternParser::parse)
               .toList();
         }

         @Override
         public UrlHandlerFilter.Builder.TrailingSlashSpec intercept(Consumer<HttpServletRequest> consumer) {
            this.interceptor = this.interceptor != null ? this.interceptor.andThen(consumer) : consumer;
            return this;
         }

         @Override
         public UrlHandlerFilter.Builder redirect(HttpStatus status) {
            UrlHandlerFilter.Handler handler = new UrlHandlerFilter.RedirectTrailingSlashHandler(status, this.interceptor);
            return DefaultBuilder.this.addHandler(this.pathPatterns, handler);
         }

         @Override
         public UrlHandlerFilter.Builder wrapRequest() {
            UrlHandlerFilter.Handler handler = new UrlHandlerFilter.RequestWrappingTrailingSlashHandler(this.interceptor);
            return DefaultBuilder.this.addHandler(this.pathPatterns, handler);
         }
      }
   }

   private interface Handler {
      boolean supports(HttpServletRequest request, RequestPath path);

      void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException;
   }

   private static final class RedirectTrailingSlashHandler extends UrlHandlerFilter.AbstractTrailingSlashHandler {
      private final HttpStatus httpStatus;

      RedirectTrailingSlashHandler(HttpStatus httpStatus, @Nullable Consumer<HttpServletRequest> interceptor) {
         super(interceptor);
         this.httpStatus = httpStatus;
      }

      @Override
      public void handleInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
         String location = this.trimTrailingSlash(request.getRequestURI());
         if (location.length() > 2 && location.startsWith("//")) {
            location = location.charAt(2) != '/' ? location.substring(1) : location;
         }

         if (StringUtils.hasText(request.getQueryString())) {
            location = location + "?" + request.getQueryString();
         }

         response.resetBuffer();
         response.setStatus(this.httpStatus.value());
         response.setHeader("Location", location);
         response.flushBuffer();
      }
   }

   private static final class RequestWrappingTrailingSlashHandler extends UrlHandlerFilter.AbstractTrailingSlashHandler {
      RequestWrappingTrailingSlashHandler(@Nullable Consumer<HttpServletRequest> interceptor) {
         super(interceptor);
      }

      @Override
      public void handleInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
         String servletPath = request.getServletPath();
         String pathInfo = request.getPathInfo();
         boolean hasPathInfo = StringUtils.hasText(pathInfo);
         HttpServletRequest var11 = new UrlHandlerFilter.TrailingSlashHttpServletRequest(
            request,
            this.trimTrailingSlash(request.getRequestURI()),
            this.trimTrailingSlash(request.getRequestURL().toString()),
            hasPathInfo ? servletPath : this.trimTrailingSlash(servletPath),
            hasPathInfo ? this.trimTrailingSlash(pathInfo) : pathInfo
         );
         RequestPath previousPath = (RequestPath)var11.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
         ServletRequestPathUtils.clearParsedRequestPath(var11);

         try {
            chain.doFilter(var11, response);
         } finally {
            if (previousPath != null) {
               ServletRequestPathUtils.setParsedRequestPath(previousPath, var11);
            }
         }
      }
   }

   private static class TrailingSlashHttpServletRequest extends HttpServletRequestWrapper {
      private final String requestURI;
      private final StringBuffer requestURL;
      private final String servletPath;
      private final String pathInfo;

      TrailingSlashHttpServletRequest(HttpServletRequest request, String requestURI, String requestURL, String servletPath, String pathInfo) {
         super(request);
         this.requestURI = requestURI;
         this.requestURL = new StringBuffer(requestURL);
         this.servletPath = servletPath;
         this.pathInfo = pathInfo;
      }

      public String getRequestURI() {
         return this.isForward() ? this.getDelegate().getRequestURI() : this.requestURI;
      }

      public StringBuffer getRequestURL() {
         return this.isForward() ? this.getDelegate().getRequestURL() : this.requestURL;
      }

      public String getServletPath() {
         return this.isForward() ? this.getDelegate().getServletPath() : this.servletPath;
      }

      public String getPathInfo() {
         return this.isForward() ? this.getDelegate().getPathInfo() : this.pathInfo;
      }

      private boolean isForward() {
         return this.getDispatcherType() == DispatcherType.FORWARD;
      }

      private HttpServletRequest getDelegate() {
         return (HttpServletRequest)this.getRequest();
      }
   }
}
