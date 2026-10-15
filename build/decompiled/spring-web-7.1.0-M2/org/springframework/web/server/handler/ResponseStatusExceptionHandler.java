package org.springframework.web.server.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

public class ResponseStatusExceptionHandler implements WebExceptionHandler {
   private static final Log logger = LogFactory.getLog(ResponseStatusExceptionHandler.class);
   private @Nullable Log warnLogger;

   public void setWarnLogCategory(String loggerName) {
      this.warnLogger = LogFactory.getLog(loggerName);
   }

   @Override
   public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
      if (!this.updateResponse(exchange.getResponse(), ex)) {
         return Mono.error(ex);
      }

      String logPrefix = exchange.getLogPrefix();
      if (this.warnLogger != null && this.warnLogger.isWarnEnabled()) {
         this.warnLogger.warn(logPrefix + this.formatError(ex, exchange.getRequest()));
      } else if (logger.isDebugEnabled()) {
         logger.debug(logPrefix + this.formatError(ex, exchange.getRequest()));
      }

      return exchange.getResponse().setComplete();
   }

   private String formatError(Throwable ex, ServerHttpRequest request) {
      String className = ex.getClass().getSimpleName();
      String message = LogFormatUtils.formatValue(ex.getMessage(), -1, true);
      String path = request.getURI().getRawPath();
      return "Resolved [" + className + ": " + message + "] for HTTP " + request.getMethod() + " " + path;
   }

   private boolean updateResponse(ServerHttpResponse response, Throwable ex) {
      boolean result = false;
      HttpStatusCode statusCode = this.determineStatus(ex);
      int code = statusCode != null ? statusCode.value() : -1;
      if (code != -1) {
         if (response.setStatusCode(statusCode)) {
            if (ex instanceof ResponseStatusException responseStatusException) {
               responseStatusException.getHeaders().forEach((name, values) -> values.forEach(value -> response.getHeaders().add(name, value)));
            }

            result = true;
         }
      } else {
         Throwable cause = ex.getCause();
         if (cause != null) {
            result = this.updateResponse(response, cause);
         }
      }

      return result;
   }

   protected @Nullable HttpStatusCode determineStatus(Throwable ex) {
      return ex instanceof ResponseStatusException responseStatusException ? responseStatusException.getStatusCode() : null;
   }
}
