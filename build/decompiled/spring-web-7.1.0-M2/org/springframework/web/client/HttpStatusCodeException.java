package org.springframework.web.client;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;

public abstract class HttpStatusCodeException extends RestClientResponseException {
   private static final long serialVersionUID = 5696801857651587810L;

   protected HttpStatusCodeException(HttpStatusCode statusCode) {
      this(statusCode, name(statusCode), null, null, null);
   }

   private static String name(HttpStatusCode statusCode) {
      return statusCode instanceof HttpStatus status ? status.name() : "";
   }

   protected HttpStatusCodeException(HttpStatusCode statusCode, String statusText) {
      this(statusCode, statusText, null, null, null);
   }

   protected HttpStatusCodeException(HttpStatusCode statusCode, String statusText, byte @Nullable [] responseBody, @Nullable Charset responseCharset) {
      this(statusCode, statusText, null, responseBody, responseCharset);
   }

   protected HttpStatusCodeException(
      HttpStatusCode statusCode, String statusText, @Nullable HttpHeaders responseHeaders, byte @Nullable [] responseBody, @Nullable Charset responseCharset
   ) {
      this(getMessage(statusCode, statusText), statusCode, statusText, responseHeaders, responseBody, responseCharset);
   }

   protected HttpStatusCodeException(
      String message,
      HttpStatusCode statusCode,
      String statusText,
      @Nullable HttpHeaders responseHeaders,
      byte @Nullable [] responseBody,
      @Nullable Charset responseCharset
   ) {
      super(message, statusCode, statusText, responseHeaders, responseBody, responseCharset);
   }

   private static String getMessage(HttpStatusCode statusCode, String statusText) {
      if (!StringUtils.hasLength(statusText) && statusCode instanceof HttpStatus status) {
         statusText = status.getReasonPhrase();
      }

      return statusCode.value() + " " + statusText;
   }
}
