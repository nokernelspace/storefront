package org.springframework.http;

import org.jspecify.annotations.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

public class HttpEntity<T> {
   public static final HttpEntity<?> EMPTY = new HttpEntity(HttpHeaders.EMPTY);
   private final HttpHeaders headers;
   private final @Nullable T body;

   protected HttpEntity() {
      this(null, (HttpHeaders)null);
   }

   public HttpEntity(T body) {
      this(body, (HttpHeaders)null);
   }

   public HttpEntity(HttpHeaders headers) {
      this(null, headers);
   }

   public HttpEntity(@Nullable T body, @Nullable HttpHeaders headers) {
      this.body = body;
      this.headers = headers != null ? headers : new HttpHeaders();
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public HttpEntity(MultiValueMap<String, String> headers) {
      this(null, headers);
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public HttpEntity(@Nullable T body, @Nullable MultiValueMap<String, String> headers) {
      this(body, headers != null ? new HttpHeaders(headers) : new HttpHeaders());
   }

   public HttpHeaders getHeaders() {
      return this.headers;
   }

   public @Nullable T getBody() {
      return this.body;
   }

   public boolean hasBody() {
      return this.body != null;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else if (other != null && other.getClass() == this.getClass()) {
         HttpEntity<?> otherEntity = (HttpEntity<?>)other;
         return ObjectUtils.nullSafeEquals(this.headers, otherEntity.headers) && ObjectUtils.nullSafeEquals(this.body, otherEntity.body);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.headers, this.body});
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder("<");
      if (this.body != null) {
         builder.append(this.body);
         builder.append(',');
      }

      builder.append(this.headers);
      builder.append('>');
      return builder.toString();
   }
}
