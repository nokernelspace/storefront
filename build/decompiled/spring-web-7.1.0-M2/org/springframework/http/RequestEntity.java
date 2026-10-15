package org.springframework.http;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

public class RequestEntity<T> extends HttpEntity<T> {
   private final @Nullable HttpMethod method;
   private final @Nullable URI url;
   private final @Nullable Type type;

   public RequestEntity(HttpMethod method, URI url) {
      this(null, (HttpHeaders)null, method, url, null);
   }

   public RequestEntity(@Nullable T body, HttpMethod method, URI url) {
      this(body, (HttpHeaders)null, method, url, null);
   }

   public RequestEntity(@Nullable T body, HttpMethod method, URI url, Type type) {
      this(body, (HttpHeaders)null, method, url, type);
   }

   public RequestEntity(HttpHeaders headers, HttpMethod method, URI url) {
      this(null, headers, method, url, null);
   }

   public RequestEntity(@Nullable T body, @Nullable HttpHeaders headers, @Nullable HttpMethod method, URI url) {
      this(body, headers, method, url, null);
   }

   public RequestEntity(@Nullable T body, @Nullable HttpHeaders headers, @Nullable HttpMethod method, @Nullable URI url, @Nullable Type type) {
      super(body, headers);
      this.method = method;
      this.url = url;
      this.type = type;
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public RequestEntity(MultiValueMap<String, String> headers, HttpMethod method, URI url) {
      this(null, headers, method, url, null);
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public RequestEntity(@Nullable T body, @Nullable MultiValueMap<String, String> headers, @Nullable HttpMethod method, URI url) {
      this(body, headers, method, url, null);
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public RequestEntity(@Nullable T body, @Nullable MultiValueMap<String, String> headers, @Nullable HttpMethod method, @Nullable URI url, @Nullable Type type) {
      super(body, headers);
      this.method = method;
      this.url = url;
      this.type = type;
   }

   public @Nullable HttpMethod getMethod() {
      return this.method;
   }

   public URI getUrl() {
      if (this.url == null) {
         throw new UnsupportedOperationException(
            "The RequestEntity was created with a URI template and variables, and there is not enough information on how to correctly expand and encode the URI template. This will be done by the RestTemplate instead with help from the UriTemplateHandler it is configured with."
         );
      } else {
         return this.url;
      }
   }

   public @Nullable Type getType() {
      if (this.type == null) {
         T body = this.getBody();
         if (body != null) {
            return body.getClass();
         }
      }

      return this.type;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else {
         return !super.equals(other)
            ? false
            : other instanceof RequestEntity<?> otherEntity
               && ObjectUtils.nullSafeEquals(this.method, otherEntity.method)
               && ObjectUtils.nullSafeEquals(this.url, otherEntity.url);
      }
   }

   @Override
   public int hashCode() {
      int hashCode = super.hashCode();
      hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.method);
      return 29 * hashCode + ObjectUtils.nullSafeHashCode(this.url);
   }

   @Override
   public String toString() {
      return format(this.getMethod(), this.getUrl().toString(), this.getBody(), this.getHeaders());
   }

   static <T> String format(@Nullable HttpMethod httpMethod, String url, @Nullable T body, HttpHeaders headers) {
      StringBuilder builder = new StringBuilder("<");
      builder.append(httpMethod);
      builder.append(' ');
      builder.append(url);
      builder.append(',');
      if (body != null) {
         builder.append(body);
         builder.append(',');
      }

      builder.append(headers);
      builder.append('>');
      return builder.toString();
   }

   public static RequestEntity.BodyBuilder method(HttpMethod method, URI url) {
      return new RequestEntity.DefaultBodyBuilder(method, url);
   }

   public static RequestEntity.BodyBuilder method(HttpMethod method, String uriTemplate, @Nullable Object... uriVariables) {
      return new RequestEntity.DefaultBodyBuilder(method, uriTemplate, uriVariables);
   }

   public static RequestEntity.BodyBuilder method(HttpMethod method, String uriTemplate, Map<String, ?> uriVariables) {
      return new RequestEntity.DefaultBodyBuilder(method, uriTemplate, (Map<String, ? extends Object>)uriVariables);
   }

   public static RequestEntity.HeadersBuilder<?> get(URI url) {
      return method(HttpMethod.GET, url);
   }

   public static RequestEntity.HeadersBuilder<?> get(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.GET, uriTemplate, uriVariables);
   }

   public static RequestEntity.HeadersBuilder<?> head(URI url) {
      return method(HttpMethod.HEAD, url);
   }

   public static RequestEntity.HeadersBuilder<?> head(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.HEAD, uriTemplate, uriVariables);
   }

   public static RequestEntity.BodyBuilder post(URI url) {
      return method(HttpMethod.POST, url);
   }

   public static RequestEntity.BodyBuilder post(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.POST, uriTemplate, uriVariables);
   }

   public static RequestEntity.BodyBuilder put(URI url) {
      return method(HttpMethod.PUT, url);
   }

   public static RequestEntity.BodyBuilder put(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.PUT, uriTemplate, uriVariables);
   }

   public static RequestEntity.BodyBuilder patch(URI url) {
      return method(HttpMethod.PATCH, url);
   }

   public static RequestEntity.BodyBuilder patch(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.PATCH, uriTemplate, uriVariables);
   }

   public static RequestEntity.HeadersBuilder<?> delete(URI url) {
      return method(HttpMethod.DELETE, url);
   }

   public static RequestEntity.HeadersBuilder<?> delete(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.DELETE, uriTemplate, uriVariables);
   }

   public static RequestEntity.HeadersBuilder<?> options(URI url) {
      return method(HttpMethod.OPTIONS, url);
   }

   public static RequestEntity.HeadersBuilder<?> options(String uriTemplate, @Nullable Object... uriVariables) {
      return method(HttpMethod.OPTIONS, uriTemplate, uriVariables);
   }

   public interface BodyBuilder extends RequestEntity.HeadersBuilder<RequestEntity.BodyBuilder> {
      RequestEntity.BodyBuilder contentLength(long contentLength);

      RequestEntity.BodyBuilder contentType(MediaType contentType);

      <T> RequestEntity<T> body(T body);

      <T> RequestEntity<T> body(T body, Type type);
   }

   private static class DefaultBodyBuilder implements RequestEntity.BodyBuilder {
      private final HttpMethod method;
      private final HttpHeaders headers = new HttpHeaders();
      private final @Nullable URI uri;
      private final @Nullable String uriTemplate;
      private final @Nullable Object @Nullable [] uriVarsArray;
      private final @Nullable Map<String, ? extends @Nullable Object> uriVarsMap;

      DefaultBodyBuilder(HttpMethod method, URI url) {
         this.method = method;
         this.uri = url;
         this.uriTemplate = null;
         this.uriVarsArray = null;
         this.uriVarsMap = null;
      }

      DefaultBodyBuilder(HttpMethod method, String uriTemplate, @Nullable Object... uriVars) {
         this.method = method;
         this.uri = null;
         this.uriTemplate = uriTemplate;
         this.uriVarsArray = uriVars;
         this.uriVarsMap = null;
      }

      DefaultBodyBuilder(HttpMethod method, String uriTemplate, Map<String, ? extends @Nullable Object> uriVars) {
         this.method = method;
         this.uri = null;
         this.uriTemplate = uriTemplate;
         this.uriVarsArray = null;
         this.uriVarsMap = uriVars;
      }

      public RequestEntity.BodyBuilder header(String headerName, String... headerValues) {
         for (String headerValue : headerValues) {
            this.headers.add(headerName, headerValue);
         }

         return this;
      }

      public RequestEntity.BodyBuilder headers(@Nullable HttpHeaders headers) {
         if (headers != null) {
            this.headers.putAll(headers);
         }

         return this;
      }

      public RequestEntity.BodyBuilder headers(Consumer<HttpHeaders> headersConsumer) {
         headersConsumer.accept(this.headers);
         return this;
      }

      public RequestEntity.BodyBuilder accept(MediaType... acceptableMediaTypes) {
         this.headers.setAccept(Arrays.asList(acceptableMediaTypes));
         return this;
      }

      public RequestEntity.BodyBuilder acceptCharset(Charset... acceptableCharsets) {
         this.headers.setAcceptCharset(Arrays.asList(acceptableCharsets));
         return this;
      }

      @Override
      public RequestEntity.BodyBuilder contentLength(long contentLength) {
         this.headers.setContentLength(contentLength);
         return this;
      }

      @Override
      public RequestEntity.BodyBuilder contentType(MediaType contentType) {
         this.headers.setContentType(contentType);
         return this;
      }

      public RequestEntity.BodyBuilder ifModifiedSince(ZonedDateTime ifModifiedSince) {
         this.headers.setIfModifiedSince(ifModifiedSince);
         return this;
      }

      public RequestEntity.BodyBuilder ifModifiedSince(Instant ifModifiedSince) {
         this.headers.setIfModifiedSince(ifModifiedSince);
         return this;
      }

      public RequestEntity.BodyBuilder ifModifiedSince(long ifModifiedSince) {
         this.headers.setIfModifiedSince(ifModifiedSince);
         return this;
      }

      public RequestEntity.BodyBuilder ifNoneMatch(String... ifNoneMatches) {
         this.headers.setIfNoneMatch(Arrays.asList(ifNoneMatches));
         return this;
      }

      @Override
      public RequestEntity<Void> build() {
         return this.buildInternal(null, null);
      }

      @Override
      public <T> RequestEntity<T> body(T body) {
         return this.buildInternal(body, null);
      }

      @Override
      public <T> RequestEntity<T> body(T body, Type type) {
         return this.buildInternal(body, type);
      }

      private <T> RequestEntity<T> buildInternal(@Nullable T body, @Nullable Type type) {
         if (this.uri != null) {
            return new RequestEntity<>(body, this.headers, this.method, this.uri, type);
         } else if (this.uriTemplate != null) {
            return new RequestEntity.UriTemplateRequestEntity<>(body, this.headers, this.method, type, this.uriTemplate, this.uriVarsArray, this.uriVarsMap);
         } else {
            throw new IllegalStateException("Neither URI nor URI template");
         }
      }
   }

   public interface HeadersBuilder<B extends RequestEntity.HeadersBuilder<B>> {
      B header(String headerName, String... headerValues);

      B headers(@Nullable HttpHeaders headers);

      B headers(Consumer<HttpHeaders> headersConsumer);

      B accept(MediaType... acceptableMediaTypes);

      B acceptCharset(Charset... acceptableCharsets);

      B ifModifiedSince(ZonedDateTime ifModifiedSince);

      B ifModifiedSince(Instant ifModifiedSince);

      B ifModifiedSince(long ifModifiedSince);

      B ifNoneMatch(String... ifNoneMatches);

      RequestEntity<Void> build();
   }

   public static class UriTemplateRequestEntity<T> extends RequestEntity<T> {
      private final String uriTemplate;
      private final @Nullable Object @Nullable [] uriVarsArray;
      private final @Nullable Map<String, ? extends @Nullable Object> uriVarsMap;

      UriTemplateRequestEntity(
         @Nullable T body,
         @Nullable HttpHeaders headers,
         @Nullable HttpMethod method,
         @Nullable Type type,
         String uriTemplate,
         @Nullable Object @Nullable [] uriVarsArray,
         @Nullable Map<String, ?> uriVarsMap
      ) {
         super(body, headers, method, null, type);
         this.uriTemplate = uriTemplate;
         this.uriVarsArray = uriVarsArray;
         this.uriVarsMap = (Map<String, ? extends Object>)uriVarsMap;
      }

      public String getUriTemplate() {
         return this.uriTemplate;
      }

      public @Nullable Object @Nullable [] getVars() {
         return this.uriVarsArray;
      }

      public @Nullable Map<String, ? extends @Nullable Object> getVarsMap() {
         return this.uriVarsMap;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         } else {
            return !super.equals(other)
               ? false
               : other instanceof RequestEntity.UriTemplateRequestEntity<?> otherEntity
                  && ObjectUtils.nullSafeEquals(this.uriTemplate, otherEntity.uriTemplate)
                  && ObjectUtils.nullSafeEquals(this.uriVarsArray, otherEntity.uriVarsArray)
                  && ObjectUtils.nullSafeEquals(this.uriVarsMap, otherEntity.uriVarsMap);
         }
      }

      @Override
      public int hashCode() {
         return 29 * super.hashCode() + ObjectUtils.nullSafeHashCode(this.uriTemplate);
      }

      @Override
      public String toString() {
         return format(this.getMethod(), this.getUriTemplate(), this.getBody(), this.getHeaders());
      }
   }
}
