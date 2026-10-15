package org.springframework.web.service.invoker;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

public class HttpRequestValues {
   private static final MultiValueMap<String, String> EMPTY_COOKIES_MAP = CollectionUtils.toMultiValueMap(Collections.emptyMap());
   private final @Nullable HttpMethod httpMethod;
   private final @Nullable URI uri;
   private final @Nullable UriBuilderFactory uriBuilderFactory;
   private final @Nullable String uriTemplate;
   private final Map<String, String> uriVariables;
   private final HttpHeaders headers;
   private final MultiValueMap<String, String> cookies;
   private final @Nullable Object version;
   private final Map<String, Object> attributes;
   private final @Nullable Object bodyValue;
   private final @Nullable ParameterizedTypeReference<?> bodyValueType;

   protected HttpRequestValues(
      @Nullable HttpMethod httpMethod,
      @Nullable URI uri,
      @Nullable UriBuilderFactory uriBuilderFactory,
      @Nullable String uriTemplate,
      Map<String, String> uriVariables,
      HttpHeaders headers,
      MultiValueMap<String, String> cookies,
      @Nullable Object version,
      Map<String, Object> attributes,
      @Nullable Object bodyValue,
      @Nullable ParameterizedTypeReference<?> bodyValueType
   ) {
      Assert.isTrue(uri != null || uriTemplate != null, "Neither URI nor URI template");
      this.httpMethod = httpMethod;
      this.uri = uri;
      this.uriBuilderFactory = uriBuilderFactory;
      this.uriTemplate = uriTemplate;
      this.uriVariables = uriVariables;
      this.headers = headers;
      this.cookies = cookies;
      this.version = version;
      this.attributes = attributes;
      this.bodyValue = bodyValue;
      this.bodyValueType = bodyValueType;
   }

   public @Nullable HttpMethod getHttpMethod() {
      return this.httpMethod;
   }

   public @Nullable URI getUri() {
      return this.uri;
   }

   public @Nullable UriBuilderFactory getUriBuilderFactory() {
      return this.uriBuilderFactory;
   }

   public @Nullable String getUriTemplate() {
      return this.uriTemplate;
   }

   public Map<String, String> getUriVariables() {
      return this.uriVariables;
   }

   public HttpHeaders getHeaders() {
      return this.headers;
   }

   public MultiValueMap<String, String> getCookies() {
      return this.cookies;
   }

   public @Nullable Object getApiVersion() {
      return this.version;
   }

   public Map<String, Object> getAttributes() {
      return this.attributes;
   }

   public @Nullable Object getBodyValue() {
      return this.bodyValue;
   }

   public @Nullable ParameterizedTypeReference<?> getBodyValueType() {
      return this.bodyValueType;
   }

   public static HttpRequestValues.Builder builder() {
      return new HttpRequestValues.Builder();
   }

   public static class Builder implements HttpRequestValues.Metadata {
      private @Nullable HttpMethod httpMethod;
      private @Nullable URI uri;
      private @Nullable UriBuilderFactory uriBuilderFactory;
      private @Nullable String uriTemplate;
      private @Nullable Map<String, String> uriVars;
      private @Nullable HttpHeaders headers;
      private @Nullable MultiValueMap<String, String> cookies;
      private @Nullable MultiValueMap<String, String> requestParams;
      private @Nullable MultiValueMap<String, Object> parts;
      private @Nullable Object version;
      private @Nullable Map<String, Object> attributes;
      private @Nullable Object bodyValue;
      private @Nullable ParameterizedTypeReference<?> bodyValueType;

      protected Builder() {
      }

      public HttpRequestValues.Builder setHttpMethod(HttpMethod httpMethod) {
         this.httpMethod = httpMethod;
         return this;
      }

      public HttpRequestValues.Builder setUri(URI uri) {
         this.uri = uri;
         return this;
      }

      public HttpRequestValues.Builder setUriBuilderFactory(@Nullable UriBuilderFactory uriBuilderFactory) {
         this.uriBuilderFactory = uriBuilderFactory;
         return this;
      }

      public HttpRequestValues.Builder setUriTemplate(String uriTemplate) {
         this.uriTemplate = uriTemplate;
         return this;
      }

      public HttpRequestValues.Builder setUriVariable(String name, String value) {
         this.uriVars = this.uriVars != null ? this.uriVars : new LinkedHashMap<>();
         this.uriVars.put(name, value);
         return this;
      }

      public HttpRequestValues.Builder setAccept(List<MediaType> acceptableMediaTypes) {
         this.initHeaders().setAccept(acceptableMediaTypes);
         return this;
      }

      public HttpRequestValues.Builder setContentType(MediaType contentType) {
         this.initHeaders().setContentType(contentType);
         return this;
      }

      public HttpRequestValues.Builder addHeader(String headerName, String... headerValues) {
         HttpHeaders headers = this.initHeaders();

         for (String headerValue : headerValues) {
            headers.add(headerName, headerValue);
         }

         return this;
      }

      public HttpRequestValues.Builder configureHeaders(Consumer<HttpHeaders> consumer) {
         consumer.accept(this.initHeaders());
         return this;
      }

      private HttpHeaders initHeaders() {
         this.headers = this.headers != null ? this.headers : new HttpHeaders();
         return this.headers;
      }

      public HttpRequestValues.Builder addCookie(String name, String... values) {
         MultiValueMap<String, String> cookies = this.initCookies();

         for (String value : values) {
            cookies.add(name, value);
         }

         return this;
      }

      public HttpRequestValues.Builder configureCookies(Consumer<MultiValueMap<String, String>> consumer) {
         consumer.accept(this.initCookies());
         return this;
      }

      private MultiValueMap<String, String> initCookies() {
         this.cookies = (MultiValueMap<String, String>)(this.cookies != null ? this.cookies : new LinkedMultiValueMap());
         return this.cookies;
      }

      public HttpRequestValues.Builder addRequestParameter(String name, String... values) {
         MultiValueMap<String, String> requestParams = this.initRequestParams();

         for (String value : values) {
            requestParams.add(name, value);
         }

         return this;
      }

      public HttpRequestValues.Builder configureRequestParams(Consumer<MultiValueMap<String, String>> consumer) {
         consumer.accept(this.initRequestParams());
         return this;
      }

      private MultiValueMap<String, String> initRequestParams() {
         this.requestParams = (MultiValueMap<String, String>)(this.requestParams != null ? this.requestParams : new LinkedMultiValueMap());
         return this.requestParams;
      }

      public HttpRequestValues.Builder addRequestPart(String name, Object part) {
         this.parts = (MultiValueMap<String, Object>)(this.parts != null ? this.parts : new LinkedMultiValueMap());
         this.parts.add(name, part);
         return this;
      }

      public HttpRequestValues.Builder setApiVersion(Object version) {
         this.version = version;
         return this;
      }

      public HttpRequestValues.Builder addAttribute(String name, Object value) {
         this.initAttributes().put(name, value);
         return this;
      }

      public HttpRequestValues.Builder configureAttributes(Consumer<Map<String, Object>> consumer) {
         consumer.accept(this.initAttributes());
         return this;
      }

      private Map<String, Object> initAttributes() {
         this.attributes = this.attributes != null ? this.attributes : new HashMap<>();
         return this.attributes;
      }

      public void setBodyValue(@Nullable Object bodyValue) {
         this.bodyValue = bodyValue;
      }

      public void setBodyValue(@Nullable Object bodyValue, @Nullable ParameterizedTypeReference<?> valueType) {
         this.setBodyValue(bodyValue);
         this.bodyValueType = valueType;
      }

      @Override
      public @Nullable HttpMethod getHttpMethod() {
         return this.httpMethod;
      }

      @Override
      public @Nullable String getUriTemplate() {
         return this.uriTemplate;
      }

      @Override
      public @Nullable MediaType getContentType() {
         return this.headers != null ? this.headers.getContentType() : null;
      }

      @Override
      public @Nullable List<MediaType> getAcceptMediaTypes() {
         return this.headers != null ? this.headers.getAccept() : null;
      }

      public HttpRequestValues build() {
         URI uri = this.uri;
         UriBuilderFactory uriBuilderFactory = this.uriBuilderFactory;
         String uriTemplate = this.uriTemplate != null ? this.uriTemplate : "";
         Map<String, String> uriVars = this.uriVars != null ? new HashMap<>(this.uriVars) : Collections.emptyMap();
         Object bodyValue = this.bodyValue;
         if (this.hasParts()) {
            Assert.isTrue(!this.hasBody(), "Expected body or request parts, not both");
            bodyValue = this.buildMultipartBody();
         }

         if (!CollectionUtils.isEmpty(this.requestParams)) {
            if (this.hasFormDataContentType()) {
               Assert.isTrue(!this.hasParts(), "Request parts not expected for a form data request");
               Assert.isTrue(!this.hasBody(), "Body not expected for a form data request");
               bodyValue = new LinkedMultiValueMap(this.requestParams);
            } else if (uri != null) {
               uri = UriComponentsBuilder.fromUri(uri).queryParams(UriUtils.encodeQueryParams(this.requestParams)).build(true).toUri();
            } else {
               uriVars = uriVars.isEmpty() ? new HashMap<>() : uriVars;
               uriTemplate = this.appendQueryParams(uriTemplate, uriVars, this.requestParams);
            }
         }

         HttpHeaders headers = HttpHeaders.EMPTY;
         if (this.headers != null) {
            headers = new HttpHeaders();
            headers.putAll(this.headers);
         }

         MultiValueMap<String, String> cookies = (MultiValueMap<String, String>)(this.cookies != null
            ? new LinkedMultiValueMap(this.cookies)
            : HttpRequestValues.EMPTY_COOKIES_MAP);
         Map<String, Object> attributes = this.attributes != null ? new HashMap<>(this.attributes) : Collections.emptyMap();
         return this.createRequestValues(
            this.httpMethod, uri, uriBuilderFactory, uriTemplate, uriVars, headers, cookies, this.version, attributes, bodyValue, this.bodyValueType
         );
      }

      protected boolean hasParts() {
         return this.parts != null;
      }

      protected boolean hasBody() {
         return this.bodyValue != null;
      }

      protected Object buildMultipartBody() {
         Assert.notNull(this.parts, "`parts` is null, was hasParts() not called?");
         return this.parts;
      }

      private boolean hasFormDataContentType() {
         return this.headers != null && MediaType.APPLICATION_FORM_URLENCODED.equals(this.headers.getContentType());
      }

      private String appendQueryParams(String uriTemplate, Map<String, String> uriVars, MultiValueMap<String, String> requestParams) {
         UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(uriTemplate);

         for (Entry<String, List<String>> entry : requestParams.entrySet()) {
            String nameVar = "queryParam-" + entry.getKey().replace(":", "%3A");
            uriVars.put(nameVar, entry.getKey());

            for (int j = 0; j < entry.getValue().size(); j++) {
               String valueVar = nameVar + "[" + j + "]";
               uriVars.put(valueVar, entry.getValue().get(j));
               uriComponentsBuilder.queryParam("{" + nameVar + "}", "{" + valueVar + "}");
            }
         }

         return uriComponentsBuilder.build().toUriString();
      }

      protected HttpRequestValues createRequestValues(
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
         return new HttpRequestValues(
            this.httpMethod, uri, uriBuilderFactory, uriTemplate, uriVars, headers, cookies, version, attributes, bodyValue, bodyValueType
         );
      }
   }

   public interface Metadata {
      @Nullable HttpMethod getHttpMethod();

      @Nullable String getUriTemplate();

      @Nullable MediaType getContentType();

      @Nullable List<MediaType> getAcceptMediaTypes();
   }

   public interface Processor {
      void process(Method method, MethodParameter[] parameters, @Nullable Object[] arguments, HttpRequestValues.Builder builder);
   }
}
