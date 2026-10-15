package org.springframework.web.client.support;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.util.UriBuilderFactory;

@Deprecated(since = "7.1", forRemoval = true)
public final class RestTemplateAdapter implements HttpExchangeAdapter {
   private final RestTemplate restTemplate;

   private RestTemplateAdapter(RestTemplate restTemplate) {
      this.restTemplate = restTemplate;
   }

   @Override
   public boolean supportsRequestAttributes() {
      return false;
   }

   @Override
   public void exchange(HttpRequestValues values) {
      this.restTemplate.exchange(this.newRequest(values), Void.class);
   }

   @Override
   public HttpHeaders exchangeForHeaders(HttpRequestValues values) {
      return this.restTemplate.exchange(this.newRequest(values), Void.class).getHeaders();
   }

   @Override
   public <T> @Nullable T exchangeForBody(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      return this.restTemplate.<T>exchange(this.newRequest(values), bodyType).getBody();
   }

   @Override
   public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues values) {
      return this.restTemplate.exchange(this.newRequest(values), Void.class);
   }

   @Override
   public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
      return this.restTemplate.exchange(this.newRequest(values), bodyType);
   }

   private <B> RequestEntity<?> newRequest(HttpRequestValues values) {
      HttpMethod httpMethod = values.getHttpMethod();
      Assert.notNull(httpMethod, "HttpMethod is required");
      RequestEntity.BodyBuilder builder;
      if (values.getUri() != null) {
         builder = RequestEntity.method(httpMethod, values.getUri());
      } else {
         if (values.getUriTemplate() == null) {
            throw new IllegalStateException("Neither full URL nor URI template");
         }

         UriBuilderFactory uriBuilderFactory = values.getUriBuilderFactory();
         if (uriBuilderFactory != null) {
            URI expanded = uriBuilderFactory.expand(values.getUriTemplate(), values.getUriVariables());
            builder = RequestEntity.method(httpMethod, expanded);
         } else {
            builder = RequestEntity.method(httpMethod, values.getUriTemplate(), values.getUriVariables());
         }
      }

      builder.headers(values.getHeaders());
      if (!values.getCookies().isEmpty()) {
         List<String> cookies = new ArrayList<>();
         values.getCookies().forEach((name, cookieValues) -> cookieValues.forEach(value -> {
            HttpCookie cookie = new HttpCookie(name, value);
            cookies.add(cookie.toString());
         }));
         builder.header("Cookie", String.join("; ", cookies));
      }

      Object body = values.getBodyValue();
      if (body == null) {
         return builder.build();
      } else {
         return values.getBodyValueType() != null ? builder.body(body, values.getBodyValueType().getType()) : builder.body(body);
      }
   }

   public static RestTemplateAdapter create(RestTemplate restTemplate) {
      return new RestTemplateAdapter(restTemplate);
   }
}
