package org.springframework.web.client.support;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.util.UriBuilderFactory;

public final class RestClientAdapter implements HttpExchangeAdapter {
   private final RestClient restClient;

   private RestClientAdapter(RestClient restClient) {
      this.restClient = restClient;
   }

   @Override
   public boolean supportsRequestAttributes() {
      return true;
   }

   @Override
   public void exchange(HttpRequestValues requestValues) {
      this.newRequest(requestValues).retrieve().toBodilessEntity();
   }

   @Override
   public HttpHeaders exchangeForHeaders(HttpRequestValues values) {
      return this.newRequest(values).retrieve().toBodilessEntity().getHeaders();
   }

   @Override
   public <T> @Nullable T exchangeForBody(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      return this.newRequest(values).retrieve().body(bodyType);
   }

   @Override
   public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues values) {
      return this.newRequest(values).retrieve().toBodilessEntity();
   }

   @Override
   public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      return this.newRequest(values).retrieve().toEntity(bodyType);
   }

   public RestClient.RequestBodySpec newRequest(HttpRequestValues values) {
      HttpMethod method = values.getHttpMethod();
      Assert.notNull(method, "HttpMethod is required");
      RestClient.RequestBodyUriSpec uriSpec = this.restClient.method(method);
      RestClient.RequestBodySpec spec = setUri(uriSpec, values);
      spec.headers(headers -> headers.putAll(values.getHeaders()));
      setCookieHeader(spec, values);
      if (values.getApiVersion() != null) {
         spec.apiVersion(values.getApiVersion());
      }

      spec.attributes(attributes -> attributes.putAll(values.getAttributes()));
      this.setBody(spec, values);
      return spec;
   }

   private static RestClient.RequestBodySpec setUri(RestClient.RequestBodyUriSpec spec, HttpRequestValues values) {
      if (values.getUri() != null) {
         return spec.uri(values.getUri());
      }

      if (values.getUriTemplate() != null) {
         UriBuilderFactory uriBuilderFactory = values.getUriBuilderFactory();
         if (uriBuilderFactory != null) {
            URI uri = uriBuilderFactory.expand(values.getUriTemplate(), values.getUriVariables());
            return spec.uri(uri);
         } else {
            return spec.uri(values.getUriTemplate(), values.getUriVariables());
         }
      } else {
         throw new IllegalStateException("Neither full URL nor URI template");
      }
   }

   private static void setCookieHeader(RestClient.RequestBodySpec spec, HttpRequestValues values) {
      if (!values.getCookies().isEmpty()) {
         List<String> cookies = new ArrayList<>();
         values.getCookies().forEach((name, cookieValues) -> cookieValues.forEach(value -> {
            HttpCookie cookie = new HttpCookie(name, value);
            cookies.add(cookie.toString());
         }));
         spec.header("Cookie", String.join("; ", cookies));
      }
   }

   private <B> void setBody(RestClient.RequestBodySpec spec, HttpRequestValues values) {
      Object body = values.getBodyValue();
      if (body != null) {
         if (body instanceof StreamingHttpOutputMessage.Body streamingBody) {
            spec.body(streamingBody);
         } else if (values.getBodyValueType() != null) {
            spec.body(body, (ParameterizedTypeReference<Object>)values.getBodyValueType());
         } else {
            spec.body(body);
         }
      }
   }

   public static RestClientAdapter create(RestClient restClient) {
      return new RestClientAdapter(restClient);
   }
}
