package org.springframework.web.client;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.MultiValueMap;

public class RestClientResponseException extends RestClientException {
   private static final long serialVersionUID = -8803556342728481792L;
   private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
   private final HttpStatusCode statusCode;
   private final String statusText;
   private final byte[] responseBody;
   private final @Nullable HttpHeaders responseHeaders;
   private final @Nullable String responseCharset;
   private transient @Nullable Function<ResolvableType, ? extends @Nullable Object> bodyConvertFunction;

   public RestClientResponseException(
      String message, int statusCode, String statusText, @Nullable HttpHeaders headers, byte @Nullable [] responseBody, @Nullable Charset responseCharset
   ) {
      this(message, HttpStatusCode.valueOf(statusCode), statusText, headers, responseBody, responseCharset);
   }

   public RestClientResponseException(
      String message,
      HttpStatusCode statusCode,
      String statusText,
      @Nullable HttpHeaders headers,
      byte @Nullable [] responseBody,
      @Nullable Charset responseCharset
   ) {
      super(message);
      this.statusCode = statusCode;
      this.statusText = statusText;
      this.responseHeaders = copyHeaders(headers);
      this.responseBody = responseBody != null ? responseBody : new byte[0];
      this.responseCharset = responseCharset != null ? responseCharset.name() : null;
   }

   private static @Nullable HttpHeaders copyHeaders(@Nullable HttpHeaders headers) {
      if (headers != null) {
         MultiValueMap<String, String> result = CollectionUtils.toMultiValueMap(new LinkedCaseInsensitiveMap(headers.size(), Locale.ROOT));
         headers.forEach((name, values) -> values.forEach(value -> result.add(name, value)));
         return HttpHeaders.readOnlyHttpHeaders(result);
      } else {
         return null;
      }
   }

   public HttpStatusCode getStatusCode() {
      return this.statusCode;
   }

   public String getStatusText() {
      return this.statusText;
   }

   public @Nullable HttpHeaders getResponseHeaders() {
      return this.responseHeaders;
   }

   public byte[] getResponseBodyAsByteArray() {
      return this.responseBody;
   }

   public String getResponseBodyAsString() {
      return this.getResponseBodyAsString(DEFAULT_CHARSET);
   }

   public String getResponseBodyAsString(Charset fallbackCharset) {
      if (this.responseCharset == null) {
         return new String(this.responseBody, fallbackCharset);
      }

      try {
         return new String(this.responseBody, this.responseCharset);
      } catch (UnsupportedEncodingException ex) {
         throw new IllegalStateException(ex);
      }
   }

   public <E> @Nullable E getResponseBodyAs(Class<E> targetType) {
      return this.getResponseBodyAs(ResolvableType.forClass(targetType));
   }

   public <E> @Nullable E getResponseBodyAs(ParameterizedTypeReference<E> targetType) {
      return this.getResponseBodyAs(ResolvableType.forType(targetType.getType()));
   }

   private <E> @Nullable E getResponseBodyAs(ResolvableType targetType) {
      Assert.state(this.bodyConvertFunction != null, "Function to convert body not set");
      return (E)this.bodyConvertFunction.apply(targetType);
   }

   public void setBodyConvertFunction(Function<ResolvableType, ? extends @Nullable Object> bodyConvertFunction) {
      this.bodyConvertFunction = bodyConvertFunction;
   }
}
