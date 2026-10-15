package org.springframework.http.server.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class OpenTelemetryServerRequestObservationConvention implements ServerRequestObservationConvention {
   private static final String NAME = "http.server.request.duration";
   private static final KeyValue METHOD_UNKNOWN = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, "_OTHER");
   private static final KeyValue SCHEME_UNKNOWN = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.SCHEME, "UNKNOWN");
   private static final KeyValue STATUS_UNKNOWN = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "UNKNOWN");
   private static final KeyValue HTTP_OUTCOME_SUCCESS = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "SUCCESS");
   private static final KeyValue HTTP_OUTCOME_UNKNOWN = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "UNKNOWN");
   private static final KeyValue ROUTE_UNKNOWN = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.ROUTE, "UNKNOWN");
   private static final KeyValue ROUTE_ROOT = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.ROUTE, "root");
   private static final KeyValue ROUTE_NOT_FOUND = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.ROUTE, "NOT_FOUND");
   private static final KeyValue ROUTE_REDIRECTION = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.ROUTE, "REDIRECTION");
   private static final KeyValue EXCEPTION_NONE = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, "none");
   private static final KeyValue HTTP_URL_UNKNOWN = KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.HighCardinalityKeyNames.URL_PATH, "UNKNOWN");
   private static final KeyValue ORIGINAL_METHOD_UNKNOWN = KeyValue.of(
      OpenTelemetryServerHttpObservationDocumentation.HighCardinalityKeyNames.METHOD_ORIGINAL, "UNKNOWN"
   );
   private static final Set<String> HTTP_METHODS = Stream.of(HttpMethod.values()).map(HttpMethod::name).collect(Collectors.toUnmodifiableSet());

   public String getName() {
      return "http.server.request.duration";
   }

   public String getContextualName(ServerRequestObservationContext context) {
      if (context.getCarrier() == null) {
         return "HTTP";
      }

      String maybeMethod = this.getMethodValue(context);
      String method = maybeMethod == null ? "HTTP" : maybeMethod;
      String target = context.getPathPattern();
      return target != null ? method + " " + target : method;
   }

   public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
      return KeyValues.of(
         new KeyValue[]{
            this.exception(context), this.method(context), this.status(context), this.pathTemplate(context), this.outcome(context), this.scheme(context)
         }
      );
   }

   public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
      return KeyValues.of(new KeyValue[]{this.methodOriginal(context), this.httpUrl(context)});
   }

   protected KeyValue method(ServerRequestObservationContext context) {
      String method = this.getMethodValue(context);
      return method != null ? KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, method) : METHOD_UNKNOWN;
   }

   protected @Nullable String getMethodValue(ServerRequestObservationContext context) {
      if (context.getCarrier() != null) {
         String httpMethod = ((HttpServletRequest)context.getCarrier()).getMethod();
         if (HTTP_METHODS.contains(httpMethod)) {
            return httpMethod;
         }
      }

      return null;
   }

   protected KeyValue scheme(ServerRequestObservationContext context) {
      return context.getCarrier() != null
         ? KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.SCHEME, ((HttpServletRequest)context.getCarrier()).getScheme())
         : SCHEME_UNKNOWN;
   }

   protected KeyValue status(ServerRequestObservationContext context) {
      return context.getResponse() != null
         ? KeyValue.of(
            OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS,
            Integer.toString(((HttpServletResponse)context.getResponse()).getStatus())
         )
         : STATUS_UNKNOWN;
   }

   protected KeyValue pathTemplate(ServerRequestObservationContext context) {
      if (context.getCarrier() != null) {
         String pattern = context.getPathPattern();
         if (pattern != null) {
            if (pattern.isEmpty()) {
               return ROUTE_ROOT;
            }

            return KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.ROUTE, pattern);
         }

         if (context.getResponse() != null) {
            HttpStatus status = HttpStatus.resolve(((HttpServletResponse)context.getResponse()).getStatus());
            if (status != null) {
               if (status.is3xxRedirection()) {
                  return ROUTE_REDIRECTION;
               }

               if (status == HttpStatus.NOT_FOUND) {
                  return ROUTE_NOT_FOUND;
               }
            }
         }
      }

      return ROUTE_UNKNOWN;
   }

   protected KeyValue exception(ServerRequestObservationContext context) {
      Throwable error = context.getError();
      return error != null
         ? KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, error.getClass().getName())
         : EXCEPTION_NONE;
   }

   protected KeyValue outcome(ServerRequestObservationContext context) {
      try {
         if (context.getResponse() != null) {
            HttpStatusCode statusCode = HttpStatusCode.valueOf(((HttpServletResponse)context.getResponse()).getStatus());
            return OpenTelemetryServerRequestObservationConvention.HttpOutcome.forStatus(statusCode);
         }
      } catch (IllegalArgumentException ex) {
         return HTTP_OUTCOME_UNKNOWN;
      }

      return HTTP_OUTCOME_UNKNOWN;
   }

   protected KeyValue httpUrl(ServerRequestObservationContext context) {
      return context.getCarrier() != null
         ? KeyValue.of(
            OpenTelemetryServerHttpObservationDocumentation.HighCardinalityKeyNames.URL_PATH, ((HttpServletRequest)context.getCarrier()).getRequestURI()
         )
         : HTTP_URL_UNKNOWN;
   }

   protected KeyValue methodOriginal(ServerRequestObservationContext context) {
      return context.getCarrier() != null
         ? KeyValue.of(
            OpenTelemetryServerHttpObservationDocumentation.HighCardinalityKeyNames.METHOD_ORIGINAL, ((HttpServletRequest)context.getCarrier()).getMethod()
         )
         : ORIGINAL_METHOD_UNKNOWN;
   }

   static class HttpOutcome {
      static KeyValue forStatus(HttpStatusCode statusCode) {
         if (statusCode.is2xxSuccessful()) {
            return OpenTelemetryServerRequestObservationConvention.HTTP_OUTCOME_SUCCESS;
         } else {
            return statusCode instanceof HttpStatus status
               ? KeyValue.of(OpenTelemetryServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, status.series().name())
               : OpenTelemetryServerRequestObservationConvention.HTTP_OUTCOME_UNKNOWN;
         }
      }
   }
}
