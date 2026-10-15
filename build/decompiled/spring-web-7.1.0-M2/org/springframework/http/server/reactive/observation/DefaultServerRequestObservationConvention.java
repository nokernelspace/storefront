package org.springframework.http.server.reactive.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;

public class DefaultServerRequestObservationConvention implements ServerRequestObservationConvention {
   private static final String DEFAULT_NAME = "http.server.requests";
   private static final KeyValue METHOD_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, "UNKNOWN");
   private static final KeyValue STATUS_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "UNKNOWN");
   private static final KeyValue HTTP_OUTCOME_SUCCESS = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "SUCCESS");
   private static final KeyValue HTTP_OUTCOME_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "UNKNOWN");
   private static final KeyValue URI_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "UNKNOWN");
   private static final KeyValue URI_ROOT = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "root");
   private static final KeyValue URI_NOT_FOUND = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "NOT_FOUND");
   private static final KeyValue URI_REDIRECTION = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "REDIRECTION");
   private static final KeyValue EXCEPTION_NONE = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, "none");
   private static final KeyValue HTTP_URL_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, "UNKNOWN");
   private static final Set<HttpMethod> HTTP_METHODS = Set.of(HttpMethod.values());
   private final String name;

   public DefaultServerRequestObservationConvention() {
      this("http.server.requests");
   }

   public DefaultServerRequestObservationConvention(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public @Nullable String getContextualName(ServerRequestObservationContext context) {
      if (context.getCarrier() != null) {
         String httpMethod = ((ServerHttpRequest)context.getCarrier()).getMethod().name().toLowerCase(Locale.ROOT);
         return context.getPathPattern() != null ? "http " + httpMethod + " " + context.getPathPattern() : "http " + httpMethod;
      } else {
         return null;
      }
   }

   public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
      return KeyValues.of(new KeyValue[]{this.exception(context), this.method(context), this.outcome(context), this.status(context), this.uri(context)});
   }

   public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
      return KeyValues.of(new KeyValue[]{this.httpUrl(context)});
   }

   protected KeyValue method(ServerRequestObservationContext context) {
      if (context.getCarrier() != null) {
         HttpMethod method = ((ServerHttpRequest)context.getCarrier()).getMethod();
         if (HTTP_METHODS.contains(method)) {
            return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, method.name());
         }
      }

      return METHOD_UNKNOWN;
   }

   protected KeyValue status(ServerRequestObservationContext context) {
      if (!context.isConnectionAborted() || context.getResponse() != null && ((ServerHttpResponse)context.getResponse()).isCommitted()) {
         return context.getResponse() != null && ((ServerHttpResponse)context.getResponse()).getStatusCode() != null
            ? KeyValue.of(
               ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS,
               Integer.toString(((ServerHttpResponse)context.getResponse()).getStatusCode().value())
            )
            : STATUS_UNKNOWN;
      } else {
         return STATUS_UNKNOWN;
      }
   }

   protected KeyValue uri(ServerRequestObservationContext context) {
      if (context.getCarrier() != null) {
         String pattern = context.getPathPattern();
         if (pattern != null) {
            if (pattern.isEmpty()) {
               return URI_ROOT;
            }

            return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, pattern);
         }

         if (context.getResponse() != null && ((ServerHttpResponse)context.getResponse()).getStatusCode() != null) {
            HttpStatus status = HttpStatus.resolve(((ServerHttpResponse)context.getResponse()).getStatusCode().value());
            if (status != null) {
               if (status.is3xxRedirection()) {
                  return URI_REDIRECTION;
               }

               if (status == HttpStatus.NOT_FOUND) {
                  return URI_NOT_FOUND;
               }
            }
         }
      }

      return URI_UNKNOWN;
   }

   protected KeyValue exception(ServerRequestObservationContext context) {
      Throwable error = context.getError();
      if (error != null) {
         String simpleName = error.getClass().getSimpleName();
         return KeyValue.of(
            ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, StringUtils.hasText(simpleName) ? simpleName : error.getClass().getName()
         );
      } else {
         return EXCEPTION_NONE;
      }
   }

   protected KeyValue outcome(ServerRequestObservationContext context) {
      if (context.isConnectionAborted()) {
         return HTTP_OUTCOME_UNKNOWN;
      } else {
         return context.getResponse() != null && ((ServerHttpResponse)context.getResponse()).getStatusCode() != null
            ? DefaultServerRequestObservationConvention.HttpOutcome.forStatus(((ServerHttpResponse)context.getResponse()).getStatusCode())
            : HTTP_OUTCOME_UNKNOWN;
      }
   }

   protected KeyValue httpUrl(ServerRequestObservationContext context) {
      return context.getCarrier() != null
         ? KeyValue.of(ServerHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, ((ServerHttpRequest)context.getCarrier()).getPath().toString())
         : HTTP_URL_UNKNOWN;
   }

   static class HttpOutcome {
      static KeyValue forStatus(HttpStatusCode statusCode) {
         if (statusCode.is2xxSuccessful()) {
            return DefaultServerRequestObservationConvention.HTTP_OUTCOME_SUCCESS;
         } else {
            return statusCode instanceof HttpStatus status
               ? KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, status.series().name())
               : DefaultServerRequestObservationConvention.HTTP_OUTCOME_UNKNOWN;
         }
      }
   }
}
