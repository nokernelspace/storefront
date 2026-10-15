package org.springframework.web.client;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

public class UnknownHttpStatusCodeException extends RestClientResponseException {
   private static final long serialVersionUID = 7103980251635005491L;

   public UnknownHttpStatusCodeException(
      int rawStatusCode, String statusText, @Nullable HttpHeaders responseHeaders, byte @Nullable [] responseBody, @Nullable Charset responseCharset
   ) {
      this("Unknown status code [" + rawStatusCode + "] " + statusText, rawStatusCode, statusText, responseHeaders, responseBody, responseCharset);
   }

   public UnknownHttpStatusCodeException(
      String message,
      int rawStatusCode,
      String statusText,
      @Nullable HttpHeaders responseHeaders,
      byte @Nullable [] responseBody,
      @Nullable Charset responseCharset
   ) {
      super(message, rawStatusCode, statusText, responseHeaders, responseBody, responseCharset);
   }
}
