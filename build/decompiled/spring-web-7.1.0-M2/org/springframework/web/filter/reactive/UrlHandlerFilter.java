package org.springframework.web.filter.reactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class UrlHandlerFilter implements WebFilter {
   private static final Log logger = LogFactory.getLog(UrlHandlerFilter.class);
   private final MultiValueMap<UrlHandlerFilter.Handler, PathPattern> handlers;

   private UrlHandlerFilter(MultiValueMap<UrlHandlerFilter.Handler, PathPattern> handlers) {
      this.handlers = new LinkedMultiValueMap(handlers);
   }

   @Override
   public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
      RequestPath path = exchange.getRequest().getPath();

      for (Entry<UrlHandlerFilter.Handler, List<PathPattern>> entry : this.handlers.entrySet()) {
         if (entry.getKey().supports(exchange)) {
            for (PathPattern pattern : entry.getValue()) {
               if (pattern.matches(path)) {
                  return entry.getKey().handle(exchange, chain);
               }
            }
         }
      }

      return chain.filter(exchange);
   }

   public static UrlHandlerFilter.Builder.TrailingSlashSpec trailingSlashHandler(String... pathPatterns) {
      return new UrlHandlerFilter.DefaultBuilder().trailingSlashHandler(pathPatterns);
   }

   private abstract static class AbstractTrailingSlashHandler implements UrlHandlerFilter.Handler {
      private static final List<Function<ServerHttpRequest, Mono<Void>>> defaultInterceptors = List.of(request -> {
         if (UrlHandlerFilter.logger.isTraceEnabled()) {
            UrlHandlerFilter.logger.trace("Handling trailing slash URL: " + request.getMethod() + " " + request.getURI());
         }

         return Mono.empty();
      });
      private final List<Function<ServerHttpRequest, Mono<Void>>> interceptors;

      protected AbstractTrailingSlashHandler(@Nullable List<Function<ServerHttpRequest, Mono<Void>>> interceptors) {
         this.interceptors = interceptors != null ? new ArrayList<>(interceptors) : defaultInterceptors;
      }

      @Override
      public boolean supports(ServerWebExchange exchange) {
         ServerHttpRequest request = exchange.getRequest();
         List<PathContainer.Element> elements = request.getPath().pathWithinApplication().elements();
         return elements.size() > 1 && elements.get(elements.size() - 1).value().equals("/");
      }

      @Override
      public Mono<Void> handle(ServerWebExchange exchange, WebFilterChain chain) {
         List<Mono<Void>> monos = new ArrayList<>(this.interceptors.size());
         this.interceptors.forEach(interceptor -> monos.add(interceptor.apply(exchange.getRequest())));
         return Flux.concat(monos).then(Mono.defer(() -> this.handleInternal(exchange, chain)));
      }

      protected abstract Mono<Void> handleInternal(ServerWebExchange exchange, WebFilterChain chain);

      protected String trimTrailingSlash(ServerHttpRequest request) {
         String path = request.getURI().getRawPath();
         int index = StringUtils.hasLength(path) ? path.lastIndexOf(47) : -1;
         return index != -1 ? path.substring(0, index) : path;
      }
   }

   public interface Builder {
      UrlHandlerFilter.Builder.TrailingSlashSpec trailingSlashHandler(String... pathPatterns);

      UrlHandlerFilter build();

      interface TrailingSlashSpec {
         UrlHandlerFilter.Builder.TrailingSlashSpec intercept(Function<ServerHttpRequest, Mono<Void>> interceptor);

         UrlHandlerFilter.Builder redirect(HttpStatusCode statusCode);

         UrlHandlerFilter.Builder mutateRequest();
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
         private @Nullable List<Function<ServerHttpRequest, Mono<Void>>> interceptors;

         private DefaultTrailingSlashSpec(String[] patterns) {
            this.pathPatterns = Arrays.stream(patterns)
               .map(pattern -> !pattern.endsWith("**") && !pattern.endsWith("/") ? pattern + "/" : pattern)
               .map(DefaultBuilder.this.patternParser::parse)
               .toList();
         }

         @Override
         public UrlHandlerFilter.Builder.TrailingSlashSpec intercept(Function<ServerHttpRequest, Mono<Void>> interceptor) {
            this.interceptors = this.interceptors != null ? this.interceptors : new ArrayList<>();
            this.interceptors.add(interceptor);
            return this;
         }

         @Override
         public UrlHandlerFilter.Builder redirect(HttpStatusCode statusCode) {
            UrlHandlerFilter.Handler handler = new UrlHandlerFilter.RedirectTrailingSlashHandler(statusCode, this.interceptors);
            return DefaultBuilder.this.addHandler(this.pathPatterns, handler);
         }

         @Override
         public UrlHandlerFilter.Builder mutateRequest() {
            UrlHandlerFilter.Handler handler = new UrlHandlerFilter.RequestWrappingTrailingSlashHandler(this.interceptors);
            return DefaultBuilder.this.addHandler(this.pathPatterns, handler);
         }
      }
   }

   private interface Handler {
      boolean supports(ServerWebExchange exchange);

      Mono<Void> handle(ServerWebExchange exchange, WebFilterChain chain);
   }

   private static final class RedirectTrailingSlashHandler extends UrlHandlerFilter.AbstractTrailingSlashHandler {
      private final HttpStatusCode statusCode;

      RedirectTrailingSlashHandler(HttpStatusCode statusCode, @Nullable List<Function<ServerHttpRequest, Mono<Void>>> interceptors) {
         super(interceptors);
         this.statusCode = statusCode;
      }

      @Override
      public Mono<Void> handleInternal(ServerWebExchange exchange, WebFilterChain chain) {
         ServerHttpRequest request = exchange.getRequest();
         String location = this.trimTrailingSlash(request);
         if (location.length() > 2 && location.startsWith("//")) {
            location = location.charAt(2) != '/' ? location.substring(1) : location;
         }

         String query = request.getURI().getRawQuery();
         if (StringUtils.hasText(query)) {
            location = location + "?" + query;
         }

         ServerHttpResponse response = exchange.getResponse();
         response.setStatusCode(this.statusCode);
         response.getHeaders().set("Location", location);
         return Mono.empty();
      }
   }

   private static final class RequestWrappingTrailingSlashHandler extends UrlHandlerFilter.AbstractTrailingSlashHandler {
      RequestWrappingTrailingSlashHandler(@Nullable List<Function<ServerHttpRequest, Mono<Void>>> interceptors) {
         super(interceptors);
      }

      @Override
      public Mono<Void> handleInternal(ServerWebExchange exchange, WebFilterChain chain) {
         ServerHttpRequest request = exchange.getRequest();
         ServerHttpRequest mutatedRequest = request.mutate().path(this.trimTrailingSlash(request)).build();
         return chain.filter(exchange.mutate().request(mutatedRequest).build());
      }
   }
}
