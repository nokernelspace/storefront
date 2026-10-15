package org.springframework.web.cors.reactive;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;

public class DefaultCorsProcessor implements CorsProcessor {
   private static final Log logger = LogFactory.getLog(DefaultCorsProcessor.class);
   private static final List<String> VARY_HEADERS = List.of("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers");
   static final String ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK = "Access-Control-Request-Private-Network";
   static final String ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK = "Access-Control-Allow-Private-Network";

   @Override
   public boolean process(@Nullable CorsConfiguration config, ServerWebExchange exchange) {
      ServerHttpRequest request = exchange.getRequest();
      if (config == null) {
         if (logger.isDebugEnabled() && CorsUtils.isCorsRequest(request)) {
            logger.debug("Skip: no CORS configuration has been provided");
         }

         return true;
      } else {
         ServerHttpResponse response = exchange.getResponse();
         HttpHeaders responseHeaders = response.getHeaders();
         List<String> varyHeaders = responseHeaders.get("Vary");
         if (varyHeaders == null) {
            responseHeaders.addAll("Vary", VARY_HEADERS);
         } else {
            for (String header : VARY_HEADERS) {
               if (!varyHeaders.contains(header)) {
                  responseHeaders.add("Vary", header);
               }
            }
         }

         try {
            if (!CorsUtils.isCorsRequest(request)) {
               return true;
            }
         } catch (IllegalArgumentException ex) {
            logger.debug("Reject: origin is malformed");
            this.rejectRequest(response);
            return false;
         }

         if (responseHeaders.getFirst("Access-Control-Allow-Origin") != null) {
            logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
            return true;
         } else {
            return this.handleInternal(exchange, config, CorsUtils.isPreFlightRequest(request));
         }
      }
   }

   protected void rejectRequest(ServerHttpResponse response) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
   }

   protected boolean handleInternal(ServerWebExchange exchange, CorsConfiguration config, boolean preFlightRequest) {
      ServerHttpRequest request = exchange.getRequest();
      ServerHttpResponse response = exchange.getResponse();
      HttpHeaders responseHeaders = response.getHeaders();
      String requestOrigin = request.getHeaders().getOrigin();
      String allowOrigin = this.checkOrigin(config, requestOrigin);
      if (allowOrigin == null) {
         logger.debug("Reject: '" + requestOrigin + "' origin is not allowed");
         this.rejectRequest(response);
         return false;
      }

      HttpMethod requestMethod = this.getMethodToUse(request, preFlightRequest);
      List<HttpMethod> allowMethods = this.checkMethods(config, requestMethod);
      if (allowMethods == null) {
         logger.debug("Reject: HTTP '" + requestMethod + "' is not allowed");
         this.rejectRequest(response);
         return false;
      }

      List<String> requestHeaders = this.getHeadersToUse(request, preFlightRequest);
      List<String> allowHeaders = this.checkHeaders(config, requestHeaders);
      if (preFlightRequest && allowHeaders == null) {
         logger.debug("Reject: headers '" + requestHeaders + "' are not allowed");
         this.rejectRequest(response);
         return false;
      }

      responseHeaders.setAccessControlAllowOrigin(allowOrigin);
      if (preFlightRequest) {
         responseHeaders.setAccessControlAllowMethods(allowMethods);
      }

      if (preFlightRequest && !CollectionUtils.isEmpty(allowHeaders)) {
         responseHeaders.setAccessControlAllowHeaders(allowHeaders);
      }

      if (!CollectionUtils.isEmpty(config.getExposedHeaders())) {
         responseHeaders.setAccessControlExposeHeaders(config.getExposedHeaders());
      }

      if (Boolean.TRUE.equals(config.getAllowCredentials())) {
         responseHeaders.setAccessControlAllowCredentials(true);
      }

      if (Boolean.TRUE.equals(config.getAllowPrivateNetwork()) && Boolean.parseBoolean(request.getHeaders().getFirst("Access-Control-Request-Private-Network"))
         )
       {
         responseHeaders.set("Access-Control-Allow-Private-Network", Boolean.toString(true));
      }

      if (preFlightRequest && config.getMaxAge() != null) {
         responseHeaders.setAccessControlMaxAge(config.getMaxAge());
      }

      return true;
   }

   protected @Nullable String checkOrigin(CorsConfiguration config, @Nullable String requestOrigin) {
      return config.checkOrigin(requestOrigin);
   }

   protected @Nullable List<HttpMethod> checkMethods(CorsConfiguration config, @Nullable HttpMethod requestMethod) {
      return config.checkHttpMethod(requestMethod);
   }

   private @Nullable HttpMethod getMethodToUse(ServerHttpRequest request, boolean isPreFlight) {
      return isPreFlight ? request.getHeaders().getAccessControlRequestMethod() : request.getMethod();
   }

   protected @Nullable List<String> checkHeaders(CorsConfiguration config, List<String> requestHeaders) {
      return config.checkHeaders(requestHeaders);
   }

   private List<String> getHeadersToUse(ServerHttpRequest request, boolean isPreFlight) {
      HttpHeaders headers = request.getHeaders();
      return isPreFlight ? headers.getAccessControlRequestHeaders() : new ArrayList<>(headers.headerNames());
   }
}
