package org.springframework.http.client.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

public class DefaultClientRequestObservationConvention implements ClientRequestObservationConvention {
   private static final String DEFAULT_NAME = "http.client.requests";
   private static final Pattern PATTERN_BEFORE_PATH = Pattern.compile("^https?://[^/]+");
   private static final KeyValue URI_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.URI, "none");
   private static final KeyValue METHOD_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, "none");
   private static final KeyValue STATUS_IO_ERROR = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "IO_ERROR");
   private static final KeyValue STATUS_CLIENT_ERROR = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "CLIENT_ERROR");
   private static final KeyValue HTTP_OUTCOME_SUCCESS = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "SUCCESS");
   private static final KeyValue HTTP_OUTCOME_UNKNOWN = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "UNKNOWN");
   private static final KeyValue CLIENT_NAME_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME, "none");
   private static final KeyValue EXCEPTION_NONE = KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, "none");
   private static final KeyValue HTTP_URL_NONE = KeyValue.of(ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, "none");
   private final String name;

   public DefaultClientRequestObservationConvention() {
      this("http.client.requests");
   }

   public DefaultClientRequestObservationConvention(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public @Nullable String getContextualName(ClientRequestObservationContext context) {
      ClientHttpRequest request = (ClientHttpRequest)context.getCarrier();
      return request != null ? "http " + request.getMethod().name().toLowerCase(Locale.ROOT) : null;
   }

   public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
      return KeyValues.of(
         new KeyValue[]{
            this.clientName(context), this.exception(context), this.method(context), this.outcome(context), this.status(context), this.uri(context)
         }
      );
   }

   protected KeyValue uri(ClientRequestObservationContext context) {
      return context.getUriTemplate() != null
         ? KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.URI, extractPath(context.getUriTemplate()))
         : URI_NONE;
   }

   private static String extractPath(String uriTemplate) {
      String path = PATTERN_BEFORE_PATH.matcher(uriTemplate).replaceFirst("");
      return path.startsWith("/") ? path : "/" + path;
   }

   protected KeyValue method(ClientRequestObservationContext context) {
      return context.getCarrier() != null
         ? KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, ((ClientHttpRequest)context.getCarrier()).getMethod().name())
         : METHOD_NONE;
   }

   protected KeyValue status(ClientRequestObservationContext context) {
      ClientHttpResponse response = (ClientHttpResponse)context.getResponse();
      if (response == null) {
         return STATUS_CLIENT_ERROR;
      }

      try {
         return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, String.valueOf(response.getStatusCode().value()));
      } catch (IOException ex) {
         return STATUS_IO_ERROR;
      }
   }

   protected KeyValue clientName(ClientRequestObservationContext context) {
      return context.getCarrier() != null && ((ClientHttpRequest)context.getCarrier()).getURI().getHost() != null
         ? KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME, ((ClientHttpRequest)context.getCarrier()).getURI().getHost())
         : CLIENT_NAME_NONE;
   }

   protected KeyValue exception(ClientRequestObservationContext context) {
      Throwable error = context.getError();
      if (error != null) {
         String simpleName = error.getClass().getSimpleName();
         return KeyValue.of(
            ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, StringUtils.hasText(simpleName) ? simpleName : error.getClass().getName()
         );
      } else {
         return EXCEPTION_NONE;
      }
   }

   protected KeyValue outcome(ClientRequestObservationContext context) {
      if (context.getResponse() != null) {
         try {
            return DefaultClientRequestObservationConvention.HttpOutcome.forStatus(((ClientHttpResponse)context.getResponse()).getStatusCode());
         } catch (IOException var3) {
         }
      }

      return HTTP_OUTCOME_UNKNOWN;
   }

   public KeyValues getHighCardinalityKeyValues(ClientRequestObservationContext context) {
      return KeyValues.of(new KeyValue[]{this.requestUri(context)});
   }

   protected KeyValue requestUri(ClientRequestObservationContext context) {
      return context.getCarrier() != null
         ? KeyValue.of(ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, ((ClientHttpRequest)context.getCarrier()).getURI().toASCIIString())
         : HTTP_URL_NONE;
   }

   static class HttpOutcome {
      static KeyValue forStatus(HttpStatusCode statusCode) {
         if (statusCode.is2xxSuccessful()) {
            return DefaultClientRequestObservationConvention.HTTP_OUTCOME_SUCCESS;
         } else {
            return statusCode instanceof HttpStatus status
               ? KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, status.series().name())
               : DefaultClientRequestObservationConvention.HTTP_OUTCOME_UNKNOWN;
         }
      }
   }
}
