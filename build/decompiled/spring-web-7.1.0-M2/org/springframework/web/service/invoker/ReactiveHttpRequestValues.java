package org.springframework.web.service.invoker;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriBuilderFactory;

public final class ReactiveHttpRequestValues extends HttpRequestValues {
   private final @Nullable Publisher<?> body;
   private final @Nullable ParameterizedTypeReference<?> bodyElementType;

   private ReactiveHttpRequestValues(
      @Nullable HttpMethod httpMethod,
      @Nullable URI uri,
      @Nullable UriBuilderFactory uriBuilderFactory,
      @Nullable String uriTemplate,
      Map<String, String> uriVars,
      HttpHeaders headers,
      MultiValueMap<String, String> cookies,
      @Nullable Object version,
      Map<String, Object> attributes,
      @Nullable Object bodyValue,
      @Nullable ParameterizedTypeReference<?> bodyValueType,
      @Nullable Publisher<?> body,
      @Nullable ParameterizedTypeReference<?> elementType
   ) {
      super(httpMethod, uri, uriBuilderFactory, uriTemplate, uriVars, headers, cookies, version, attributes, bodyValue, bodyValueType);
      this.body = body;
      this.bodyElementType = elementType;
   }

   public @Nullable Publisher<?> getBodyPublisher() {
      return this.body;
   }

   public @Nullable ParameterizedTypeReference<?> getBodyPublisherElementType() {
      return this.bodyElementType;
   }

   public static ReactiveHttpRequestValues.Builder builder() {
      return new ReactiveHttpRequestValues.Builder();
   }

   public static final class Builder extends HttpRequestValues.Builder {
      private @Nullable MultipartBodyBuilder multipartBuilder;
      private @Nullable Publisher<?> body;
      private @Nullable ParameterizedTypeReference<?> bodyElementType;

      private Builder() {
      }

      public ReactiveHttpRequestValues.Builder setHttpMethod(HttpMethod httpMethod) {
         super.setHttpMethod(httpMethod);
         return this;
      }

      public ReactiveHttpRequestValues.Builder setUri(URI uri) {
         super.setUri(uri);
         return this;
      }

      public ReactiveHttpRequestValues.Builder setUriBuilderFactory(@Nullable UriBuilderFactory uriBuilderFactory) {
         super.setUriBuilderFactory(uriBuilderFactory);
         return this;
      }

      public ReactiveHttpRequestValues.Builder setUriTemplate(String uriTemplate) {
         super.setUriTemplate(uriTemplate);
         return this;
      }

      public ReactiveHttpRequestValues.Builder setUriVariable(String name, String value) {
         super.setUriVariable(name, value);
         return this;
      }

      public ReactiveHttpRequestValues.Builder setAccept(List<MediaType> acceptableMediaTypes) {
         super.setAccept(acceptableMediaTypes);
         return this;
      }

      public ReactiveHttpRequestValues.Builder setContentType(MediaType contentType) {
         super.setContentType(contentType);
         return this;
      }

      public ReactiveHttpRequestValues.Builder addHeader(String headerName, String... headerValues) {
         super.addHeader(headerName, headerValues);
         return this;
      }

      public ReactiveHttpRequestValues.Builder addCookie(String name, String... values) {
         super.addCookie(name, values);
         return this;
      }

      public ReactiveHttpRequestValues.Builder addRequestParameter(String name, String... values) {
         super.addRequestParameter(name, values);
         return this;
      }

      public ReactiveHttpRequestValues.Builder addAttribute(String name, Object value) {
         super.addAttribute(name, value);
         return this;
      }

      public ReactiveHttpRequestValues.Builder addRequestPart(String name, Object part) {
         this.multipartBuilder = this.multipartBuilder != null ? this.multipartBuilder : new MultipartBodyBuilder();
         this.multipartBuilder.part(name, part);
         return this;
      }

      public <T, P extends Publisher<T>> ReactiveHttpRequestValues.Builder addRequestPartPublisher(
         String name, P publisher, ParameterizedTypeReference<T> elementTye
      ) {
         this.multipartBuilder = this.multipartBuilder != null ? this.multipartBuilder : new MultipartBodyBuilder();
         this.multipartBuilder.asyncPart(name, publisher, elementTye);
         return this;
      }

      @Override
      public void setBodyValue(@Nullable Object bodyValue) {
         super.setBodyValue(bodyValue);
         this.body = null;
         this.bodyElementType = null;
      }

      public <T, P extends Publisher<T>> void setBodyPublisher(P body, ParameterizedTypeReference<T> elementTye) {
         this.body = body;
         this.bodyElementType = elementTye;
         super.setBodyValue(null);
      }

      public ReactiveHttpRequestValues build() {
         return (ReactiveHttpRequestValues)super.build();
      }

      @Override
      protected boolean hasParts() {
         return this.multipartBuilder != null;
      }

      @Override
      protected boolean hasBody() {
         return super.hasBody() || this.body != null;
      }

      @Override
      protected Object buildMultipartBody() {
         Assert.notNull(this.multipartBuilder, "`multipartBuilder` is null, was hasParts() not called?");
         return this.multipartBuilder.build();
      }

      protected ReactiveHttpRequestValues createRequestValues(
         @Nullable HttpMethod httpMethod,
         @Nullable URI uri,
         @Nullable UriBuilderFactory uriBuilderFactory,
         @Nullable String uriTemplate,
         Map<String, String> uriVars,
         HttpHeaders headers,
         MultiValueMap<String, String> cookies,
         @Nullable Object version,
         Map<String, Object> attributes,
         @Nullable Object bodyValue,
         @Nullable ParameterizedTypeReference<?> bodyValueType
      ) {
         return new ReactiveHttpRequestValues(
            httpMethod,
            uri,
            uriBuilderFactory,
            uriTemplate,
            uriVars,
            headers,
            cookies,
            version,
            attributes,
            bodyValue,
            bodyValueType,
            this.body,
            this.bodyElementType
         );
      }
   }
}
