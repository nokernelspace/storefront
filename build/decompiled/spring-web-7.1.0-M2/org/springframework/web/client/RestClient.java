package org.springframework.web.client;

import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.lang.CheckReturnValue;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;

public interface RestClient {
   RestClient.RequestHeadersUriSpec<?> get();

   RestClient.RequestHeadersUriSpec<?> head();

   RestClient.RequestBodyUriSpec post();

   RestClient.RequestBodyUriSpec put();

   RestClient.RequestBodyUriSpec patch();

   RestClient.RequestHeadersUriSpec<?> delete();

   RestClient.RequestHeadersUriSpec<?> options();

   RestClient.RequestBodyUriSpec method(HttpMethod method);

   RestClient.Builder mutate();

   static RestClient create() {
      return new DefaultRestClientBuilder().build();
   }

   static RestClient create(String baseUrl) {
      return new DefaultRestClientBuilder().baseUrl(baseUrl).build();
   }

   static RestClient create(URI baseUrl) {
      return new DefaultRestClientBuilder().baseUrl(baseUrl).build();
   }

   static RestClient create(RestTemplate restTemplate) {
      return new DefaultRestClientBuilder(restTemplate).build();
   }

   static RestClient.Builder builder() {
      return new DefaultRestClientBuilder();
   }

   static RestClient.Builder builder(RestTemplate restTemplate) {
      return new DefaultRestClientBuilder(restTemplate);
   }

   interface Builder {
      RestClient.Builder baseUrl(String baseUrl);

      RestClient.Builder baseUrl(URI baseUrl);

      RestClient.Builder defaultUriVariables(Map<String, ?> defaultUriVariables);

      RestClient.Builder uriBuilderFactory(UriBuilderFactory uriBuilderFactory);

      RestClient.Builder defaultHeader(String header, String... values);

      RestClient.Builder defaultHeaders(Consumer<HttpHeaders> headersConsumer);

      RestClient.Builder defaultCookie(String cookie, String... values);

      RestClient.Builder defaultCookies(Consumer<MultiValueMap<String, String>> cookiesConsumer);

      RestClient.Builder defaultApiVersion(Object version);

      RestClient.Builder apiVersionInserter(@Nullable ApiVersionInserter apiVersionInserter);

      RestClient.Builder defaultRequest(Consumer<RestClient.RequestHeadersSpec<?>> defaultRequest);

      RestClient.Builder defaultStatusHandler(Predicate<HttpStatusCode> statusPredicate, RestClient.ResponseSpec.ErrorHandler errorHandler);

      @Deprecated(since = "7.1", forRemoval = true)
      RestClient.Builder defaultStatusHandler(ResponseErrorHandler errorHandler);

      RestClient.Builder requestInterceptor(ClientHttpRequestInterceptor interceptor);

      RestClient.Builder requestInterceptors(Consumer<List<ClientHttpRequestInterceptor>> interceptorsConsumer);

      RestClient.Builder bufferContent(BiPredicate<URI, HttpMethod> predicate);

      RestClient.Builder requestInitializer(ClientHttpRequestInitializer initializer);

      RestClient.Builder requestInitializers(Consumer<List<ClientHttpRequestInitializer>> initializersConsumer);

      RestClient.Builder requestFactory(ClientHttpRequestFactory requestFactory);

      RestClient.Builder configureMessageConverters(Consumer<HttpMessageConverters.ClientBuilder> configurer);

      @Deprecated(since = "7.0", forRemoval = true)
      RestClient.Builder messageConverters(Iterable<HttpMessageConverter<?>> messageConverters);

      @Deprecated(since = "7.0", forRemoval = true)
      RestClient.Builder messageConverters(Consumer<List<HttpMessageConverter<?>>> configurer);

      RestClient.Builder observationRegistry(ObservationRegistry observationRegistry);

      RestClient.Builder observationConvention(ClientRequestObservationConvention observationConvention);

      RestClient.Builder apply(Consumer<RestClient.Builder> builderConsumer);

      RestClient.Builder clone();

      RestClient build();
   }

   interface RequestBodySpec extends RestClient.RequestHeadersSpec<RestClient.RequestBodySpec> {
      RestClient.RequestBodySpec contentLength(long contentLength);

      RestClient.RequestBodySpec contentType(MediaType contentType);

      RestClient.RequestBodySpec body(Object body);

      <T> RestClient.RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType);

      RestClient.RequestBodySpec body(StreamingHttpOutputMessage.Body body);

      RestClient.RequestBodySpec hint(String key, Object value);
   }

   interface RequestBodyUriSpec extends RestClient.RequestBodySpec, RestClient.RequestHeadersUriSpec<RestClient.RequestBodySpec> {
   }

   interface RequestHeadersSpec<S extends RestClient.RequestHeadersSpec<S>> {
      S accept(MediaType... acceptableMediaTypes);

      S acceptCharset(Charset... acceptableCharsets);

      S cookie(String name, String value);

      S cookies(Consumer<MultiValueMap<String, String>> cookiesConsumer);

      S ifModifiedSince(ZonedDateTime ifModifiedSince);

      S ifNoneMatch(String... ifNoneMatches);

      S header(String headerName, String... headerValues);

      S headers(Consumer<HttpHeaders> headersConsumer);

      S apiVersion(@Nullable Object version);

      S attribute(String name, Object value);

      S attributes(Consumer<Map<String, Object>> attributesConsumer);

      S httpRequest(Consumer<ClientHttpRequest> requestConsumer);

      @CheckReturnValue
      RestClient.ResponseSpec retrieve();

      default <T> T exchange(RestClient.RequestHeadersSpec.ExchangeFunction<T> exchangeFunction) {
         return this.exchange(exchangeFunction, true);
      }

      default <T> T exchangeForRequiredValue(RestClient.RequestHeadersSpec.RequiredValueExchangeFunction<T> exchangeFunction) {
         return this.exchangeForRequiredValue(exchangeFunction, true);
      }

      <T> T exchange(RestClient.RequestHeadersSpec.ExchangeFunction<T> exchangeFunction, boolean close);

      <T> T exchangeForRequiredValue(RestClient.RequestHeadersSpec.RequiredValueExchangeFunction<T> exchangeFunction, boolean close);

      interface ConvertibleClientHttpResponse extends ClientHttpResponse {
         <T> @Nullable T bodyTo(Class<T> bodyType);

         <T> @Nullable T bodyTo(ParameterizedTypeReference<T> bodyType);

         RestClientResponseException createException() throws IOException;
      }

      @FunctionalInterface
      interface ExchangeFunction<T> {
         T exchange(HttpRequest clientRequest, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse clientResponse) throws IOException;
      }

      @FunctionalInterface
      interface RequiredValueExchangeFunction<T> extends RestClient.RequestHeadersSpec.ExchangeFunction<T> {
         @Override
         T exchange(HttpRequest clientRequest, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse clientResponse) throws IOException;
      }
   }

   interface RequestHeadersUriSpec<S extends RestClient.RequestHeadersSpec<S>> extends RestClient.UriSpec<S>, RestClient.RequestHeadersSpec<S> {
   }

   interface ResponseSpec {
      RestClient.ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate, RestClient.ResponseSpec.ErrorHandler errorHandler);

      @Deprecated(since = "7.1", forRemoval = true)
      RestClient.ResponseSpec onStatus(ResponseErrorHandler errorHandler);

      <T> @Nullable T body(Class<T> bodyType);

      <T> T requiredBody(Class<T> bodyType);

      <T> @Nullable T body(ParameterizedTypeReference<T> bodyType);

      <T> T requiredBody(ParameterizedTypeReference<T> bodyType);

      <T> ResponseEntity<T> toEntity(Class<T> bodyType);

      <T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType);

      ResponseEntity<Void> toBodilessEntity();

      RestClient.ResponseSpec hint(String key, Object value);

      @FunctionalInterface
      interface ErrorHandler {
         void handle(HttpRequest request, ClientHttpResponse response) throws IOException;
      }
   }

   interface UriSpec<S extends RestClient.RequestHeadersSpec<?>> {
      S uri(URI uri);

      S uri(String uri, @Nullable Object... uriVariables);

      S uri(String uri, Map<String, ? extends @Nullable Object> uriVariables);

      S uri(String uri, Function<UriBuilder, URI> uriFunction);

      S uri(Function<UriBuilder, URI> uriFunction);
   }
}
