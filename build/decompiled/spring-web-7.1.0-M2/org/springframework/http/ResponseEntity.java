package org.springframework.http;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

public class ResponseEntity<T> extends HttpEntity<T> {
   private final HttpStatusCode status;

   public ResponseEntity(HttpStatusCode status) {
      this(null, (HttpHeaders)null, status);
   }

   public ResponseEntity(@Nullable T body, HttpStatusCode status) {
      this(body, (HttpHeaders)null, status);
   }

   public ResponseEntity(HttpHeaders headers, HttpStatusCode status) {
      this(null, headers, status);
   }

   public ResponseEntity(@Nullable T body, @Nullable HttpHeaders headers, int rawStatus) {
      this(body, headers, HttpStatusCode.valueOf(rawStatus));
   }

   public ResponseEntity(@Nullable T body, @Nullable HttpHeaders headers, HttpStatusCode statusCode) {
      super(body, headers);
      Assert.notNull(statusCode, "HttpStatusCode must not be null");
      this.status = statusCode;
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ResponseEntity(MultiValueMap<String, String> headers, HttpStatusCode status) {
      this(null, headers, status);
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ResponseEntity(@Nullable T body, @Nullable MultiValueMap<String, String> headers, int rawStatus) {
      this(body, headers, HttpStatusCode.valueOf(rawStatus));
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public ResponseEntity(@Nullable T body, @Nullable MultiValueMap<String, String> headers, HttpStatusCode statusCode) {
      super(body, headers);
      Assert.notNull(statusCode, "HttpStatusCode must not be null");
      this.status = statusCode;
   }

   public HttpStatusCode getStatusCode() {
      return this.status;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else {
         return !super.equals(other) ? false : other instanceof ResponseEntity<?> otherEntity && ObjectUtils.nullSafeEquals(this.status, otherEntity.status);
      }
   }

   @Override
   public int hashCode() {
      return 29 * super.hashCode() + ObjectUtils.nullSafeHashCode(this.status);
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder("<");
      builder.append(this.status);
      if (this.status instanceof HttpStatus httpStatus) {
         builder.append(' ');
         builder.append(httpStatus.getReasonPhrase());
      }

      builder.append(',');
      T body = this.getBody();
      HttpHeaders headers = this.getHeaders();
      if (body != null) {
         builder.append(body);
         builder.append(',');
      }

      builder.append(headers);
      builder.append('>');
      return builder.toString();
   }

   public static ResponseEntity.BodyBuilder status(HttpStatusCode status) {
      Assert.notNull(status, "HttpStatusCode must not be null");
      return new ResponseEntity.DefaultBuilder(status);
   }

   public static ResponseEntity.BodyBuilder status(int status) {
      return new ResponseEntity.DefaultBuilder(status);
   }

   public static ResponseEntity.BodyBuilder ok() {
      return status(HttpStatus.OK);
   }

   public static <T> ResponseEntity<T> ok(@Nullable T body) {
      return ok().body(body);
   }

   public static <T> ResponseEntity<T> of(Optional<T> body) {
      Assert.notNull(body, "Body must not be null");
      return body.<ResponseEntity<T>>map(ResponseEntity::ok).orElseGet(() -> notFound().build());
   }

   public static ResponseEntity.HeadersBuilder<?> of(ProblemDetail body) {
      return new ResponseEntity.DefaultBuilder(body.getStatus()) {
         @Override
         public <T> ResponseEntity<T> build() {
            return this.body((T)body);
         }
      };
   }

   public static <T> ResponseEntity<T> ofNullable(@Nullable T body) {
      return body == null ? notFound().build() : ok(body);
   }

   public static ResponseEntity.BodyBuilder created(URI location) {
      return status(HttpStatus.CREATED).location(location);
   }

   public static ResponseEntity.BodyBuilder accepted() {
      return status(HttpStatus.ACCEPTED);
   }

   public static ResponseEntity.HeadersBuilder<?> noContent() {
      return status(HttpStatus.NO_CONTENT);
   }

   public static ResponseEntity.BodyBuilder badRequest() {
      return status(HttpStatus.BAD_REQUEST);
   }

   public static ResponseEntity.HeadersBuilder<?> notFound() {
      return status(HttpStatus.NOT_FOUND);
   }

   public static ResponseEntity.BodyBuilder unprocessableContent() {
      return status(HttpStatus.UNPROCESSABLE_CONTENT);
   }

   @Deprecated(since = "7.0")
   public static ResponseEntity.BodyBuilder unprocessableEntity() {
      return status(HttpStatus.UNPROCESSABLE_ENTITY);
   }

   public static ResponseEntity.BodyBuilder internalServerError() {
      return status(HttpStatus.INTERNAL_SERVER_ERROR);
   }

   public interface BodyBuilder extends ResponseEntity.HeadersBuilder<ResponseEntity.BodyBuilder> {
      ResponseEntity.BodyBuilder contentLength(long contentLength);

      ResponseEntity.BodyBuilder contentType(MediaType contentType);

      <T> ResponseEntity<T> body(@Nullable T body);
   }

   private static class DefaultBuilder implements ResponseEntity.BodyBuilder {
      private final HttpStatusCode statusCode;
      private final HttpHeaders headers = new HttpHeaders();

      public DefaultBuilder(int statusCode) {
         this(HttpStatusCode.valueOf(statusCode));
      }

      public DefaultBuilder(HttpStatusCode statusCode) {
         this.statusCode = statusCode;
      }

      public ResponseEntity.BodyBuilder header(String headerName, String... headerValues) {
         for (String headerValue : headerValues) {
            this.headers.add(headerName, headerValue);
         }

         return this;
      }

      public ResponseEntity.BodyBuilder headers(@Nullable HttpHeaders headers) {
         if (headers != null) {
            this.headers.putAll(headers);
         }

         return this;
      }

      public ResponseEntity.BodyBuilder headers(Consumer<HttpHeaders> headersConsumer) {
         headersConsumer.accept(this.headers);
         return this;
      }

      public ResponseEntity.BodyBuilder allow(HttpMethod... allowedMethods) {
         this.headers.setAllow(new LinkedHashSet<>(Arrays.asList(allowedMethods)));
         return this;
      }

      @Override
      public ResponseEntity.BodyBuilder contentLength(long contentLength) {
         this.headers.setContentLength(contentLength);
         return this;
      }

      @Override
      public ResponseEntity.BodyBuilder contentType(MediaType contentType) {
         this.headers.setContentType(contentType);
         return this;
      }

      public ResponseEntity.BodyBuilder eTag(@Nullable String tag) {
         this.headers.setETag(tag);
         return this;
      }

      public ResponseEntity.BodyBuilder lastModified(ZonedDateTime date) {
         this.headers.setLastModified(date);
         return this;
      }

      public ResponseEntity.BodyBuilder lastModified(Instant date) {
         this.headers.setLastModified(date);
         return this;
      }

      public ResponseEntity.BodyBuilder lastModified(long date) {
         this.headers.setLastModified(date);
         return this;
      }

      public ResponseEntity.BodyBuilder location(URI location) {
         this.headers.setLocation(location);
         return this;
      }

      public ResponseEntity.BodyBuilder cacheControl(CacheControl cacheControl) {
         this.headers.setCacheControl(cacheControl);
         return this;
      }

      public ResponseEntity.BodyBuilder varyBy(String... requestHeaders) {
         this.headers.setVary(Arrays.asList(requestHeaders));
         return this;
      }

      @Override
      public <T> ResponseEntity<T> build() {
         return this.body(null);
      }

      @Override
      public <T> ResponseEntity<T> body(@Nullable T body) {
         return new ResponseEntity<>(body, this.headers, this.statusCode);
      }
   }

   public interface HeadersBuilder<B extends ResponseEntity.HeadersBuilder<B>> {
      B header(String headerName, String... headerValues);

      B headers(@Nullable HttpHeaders headers);

      B headers(Consumer<HttpHeaders> headersConsumer);

      B allow(HttpMethod... allowedMethods);

      B eTag(@Nullable String etag);

      B lastModified(ZonedDateTime lastModified);

      B lastModified(Instant lastModified);

      B lastModified(long lastModified);

      B location(URI location);

      B cacheControl(CacheControl cacheControl);

      B varyBy(String... requestHeaders);

      <T> ResponseEntity<T> build();
   }
}
