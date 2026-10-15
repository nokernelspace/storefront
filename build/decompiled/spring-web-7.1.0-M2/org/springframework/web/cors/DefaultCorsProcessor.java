package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.CollectionUtils;

public class DefaultCorsProcessor implements CorsProcessor {
   private static final Log logger = LogFactory.getLog(DefaultCorsProcessor.class);
   static final String ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK = "Access-Control-Request-Private-Network";
   static final String ACCESS_CONTROL_ALLOW_PRIVATE_NETWORK = "Access-Control-Allow-Private-Network";

   @Override
   public boolean processRequest(@Nullable CorsConfiguration config, HttpServletRequest request, HttpServletResponse response) throws IOException {
      if (config == null) {
         if (logger.isDebugEnabled() && CorsUtils.isCorsRequest(request)) {
            logger.debug("Skip: no CORS configuration has been provided");
         }

         return true;
      } else {
         Collection<String> varyHeaders = response.getHeaders("Vary");
         if (!varyHeaders.contains("Origin")) {
            response.addHeader("Vary", "Origin");
         }

         if (!varyHeaders.contains("Access-Control-Request-Method")) {
            response.addHeader("Vary", "Access-Control-Request-Method");
         }

         if (!varyHeaders.contains("Access-Control-Request-Headers")) {
            response.addHeader("Vary", "Access-Control-Request-Headers");
         }

         try {
            if (!CorsUtils.isCorsRequest(request)) {
               return true;
            }
         } catch (IllegalArgumentException ex) {
            logger.debug("Reject: origin is malformed");
            this.rejectRequest(new ServletServerHttpResponse(response));
            return false;
         }

         if (response.getHeader("Access-Control-Allow-Origin") != null) {
            logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
            return true;
         } else {
            return this.handleInternal(
               new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), config, CorsUtils.isPreFlightRequest(request)
            );
         }
      }
   }

   protected void rejectRequest(ServerHttpResponse response) throws IOException {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      response.getBody().write("Invalid CORS request".getBytes(StandardCharsets.UTF_8));
      response.flush();
   }

   protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response, CorsConfiguration config, boolean preFlightRequest) throws IOException {
      String requestOrigin = request.getHeaders().getOrigin();
      String allowOrigin = this.checkOrigin(config, requestOrigin);
      HttpHeaders responseHeaders = response.getHeaders();
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

      response.flush();
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
