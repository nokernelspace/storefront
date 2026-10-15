package org.springframework.web.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation.Scope;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;

final class DefaultRestClient implements RestClient {
   private static final Log logger = LogFactory.getLog(DefaultRestClient.class);
   private static final ClientRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultClientRequestObservationConvention();
   private static final String URI_TEMPLATE_ATTRIBUTE = RestClient.class.getName() + ".uriTemplate";
   private final ClientHttpRequestFactory clientRequestFactory;
   private volatile @Nullable ClientHttpRequestFactory interceptingRequestFactory;
   private final @Nullable List<ClientHttpRequestInitializer> initializers;
   private final @Nullable List<ClientHttpRequestInterceptor> interceptors;
   private final @Nullable BiPredicate<URI, HttpMethod> bufferingPredicate;
   private final UriBuilderFactory uriBuilderFactory;
   private final @Nullable HttpHeaders defaultHeaders;
   private final @Nullable MultiValueMap<String, String> defaultCookies;
   private final @Nullable Object defaultApiVersion;
   private final @Nullable ApiVersionInserter apiVersionInserter;
   private final @Nullable Consumer<RestClient.RequestHeadersSpec<?>> defaultRequest;
   private final List<StatusHandler> defaultStatusHandlers;
   private final DefaultRestClientBuilder builder;
   private final List<HttpMessageConverter<?>> messageConverters;
   private final ObservationRegistry observationRegistry;
   private final @Nullable ClientRequestObservationConvention observationConvention;

   DefaultRestClient(
      ClientHttpRequestFactory clientRequestFactory,
      @Nullable List<ClientHttpRequestInterceptor> interceptors,
      @Nullable BiPredicate<URI, HttpMethod> bufferingPredicate,
      @Nullable List<ClientHttpRequestInitializer> initializers,
      UriBuilderFactory uriBuilderFactory,
      @Nullable HttpHeaders defaultHeaders,
      @Nullable MultiValueMap<String, String> defaultCookies,
      @Nullable Object defaultApiVersion,
      @Nullable ApiVersionInserter apiVersionInserter,
      @Nullable Consumer<RestClient.RequestHeadersSpec<?>> defaultRequest,
      @Nullable List<StatusHandler> statusHandlers,
      List<HttpMessageConverter<?>> messageConverters,
      ObservationRegistry observationRegistry,
      @Nullable ClientRequestObservationConvention observationConvention,
      DefaultRestClientBuilder builder
   ) {
      this.clientRequestFactory = clientRequestFactory;
      this.initializers = initializers;
      this.interceptors = interceptors;
      this.bufferingPredicate = bufferingPredicate;
      this.uriBuilderFactory = uriBuilderFactory;
      this.defaultHeaders = defaultHeaders;
      this.defaultCookies = defaultCookies;
      this.defaultApiVersion = defaultApiVersion;
      this.apiVersionInserter = apiVersionInserter;
      this.defaultRequest = defaultRequest;
      this.defaultStatusHandlers = statusHandlers != null ? new ArrayList<>(statusHandlers) : new ArrayList<>();
      this.messageConverters = messageConverters;
      this.observationRegistry = observationRegistry;
      this.observationConvention = observationConvention;
      this.builder = builder;
   }

   @Override
   public RestClient.RequestHeadersUriSpec<?> get() {
      return this.methodInternal(HttpMethod.GET);
   }

   @Override
   public RestClient.RequestHeadersUriSpec<?> head() {
      return this.methodInternal(HttpMethod.HEAD);
   }

   @Override
   public RestClient.RequestBodyUriSpec post() {
      return this.methodInternal(HttpMethod.POST);
   }

   @Override
   public RestClient.RequestBodyUriSpec put() {
      return this.methodInternal(HttpMethod.PUT);
   }

   @Override
   public RestClient.RequestBodyUriSpec patch() {
      return this.methodInternal(HttpMethod.PATCH);
   }

   @Override
   public RestClient.RequestHeadersUriSpec<?> delete() {
      return this.methodInternal(HttpMethod.DELETE);
   }

   @Override
   public RestClient.RequestHeadersUriSpec<?> options() {
      return this.methodInternal(HttpMethod.OPTIONS);
   }

   @Override
   public RestClient.RequestBodyUriSpec method(HttpMethod method) {
      Assert.notNull(method, "HttpMethod must not be null");
      return this.methodInternal(method);
   }

   private RestClient.RequestBodyUriSpec methodInternal(HttpMethod httpMethod) {
      DefaultRestClient.DefaultRequestBodyUriSpec spec = new DefaultRestClient.DefaultRequestBodyUriSpec(httpMethod);
      if (this.defaultRequest != null) {
         this.defaultRequest.accept(spec);
      }

      return spec;
   }

   @Override
   public RestClient.Builder mutate() {
      return new DefaultRestClientBuilder(this.builder);
   }

   private <T> @Nullable T readWithMessageConverters(
      ClientHttpResponse clientResponse, Runnable callback, Type bodyType, Class<T> bodyClass, @Nullable Map<String, Object> hints
   ) {
      try {
         callback.run();
         if (bodyClass.equals(InputStream.class)) {
            IntrospectingClientHttpResponse responseWrapper = new IntrospectingClientHttpResponse(clientResponse);
            return (T)(responseWrapper.hasMessageBody() && !responseWrapper.hasEmptyMessageBody() ? responseWrapper.getBody() : null);
         } else {
            return RestClientUtils.readWithMessageConverters(clientResponse, this.messageConverters, bodyType, bodyClass, hints);
         }
      } catch (UncheckedIOException | IOException exc) {
         Throwable cause;
         if (exc instanceof UncheckedIOException uncheckedIOException) {
            cause = uncheckedIOException.getCause();
         } else {
            cause = exc;
         }

         throw new RestClientException(
            "Error while extracting response for type ["
               + ResolvableType.forType(bodyType)
               + "] and content type ["
               + RestClientUtils.getContentType(clientResponse)
               + "]",
            cause
         );
      }
   }

   private static <T> Class<T> bodyClass(Type type) {
      if (type instanceof Class<?> clazz) {
         return (Class<T>)clazz;
      } else {
         return (Class<T>)(type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawType
            ? rawType
            : Object.class);
      }
   }

   private class DefaultConvertibleClientHttpResponse implements RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse {
      private final ClientHttpResponse delegate;
      private final @Nullable Map<String, Object> hints;

      public DefaultConvertibleClientHttpResponse(ClientHttpResponse delegate, @Nullable Map<String, Object> hints) {
         this.delegate = delegate;
         this.hints = hints;
      }

      @Override
      public HttpStatusCode getStatusCode() throws IOException {
         return this.delegate.getStatusCode();
      }

      @Override
      public String getStatusText() throws IOException {
         return this.delegate.getStatusText();
      }

      @Override
      public HttpHeaders getHeaders() {
         return this.delegate.getHeaders();
      }

      @Override
      public InputStream getBody() throws IOException {
         return this.delegate.getBody();
      }

      @Override
      public void close() {
         this.delegate.close();
      }

      @Override
      public <T> @Nullable T bodyTo(Class<T> bodyType) {
         return DefaultRestClient.this.readWithMessageConverters(this.delegate, () -> {}, bodyType, bodyType, this.hints);
      }

      @Override
      public <T> @Nullable T bodyTo(ParameterizedTypeReference<T> bodyType) {
         Type type = bodyType.getType();
         Class<T> bodyClass = DefaultRestClient.bodyClass(type);
         return DefaultRestClient.this.readWithMessageConverters(this.delegate, () -> {}, type, bodyClass, this.hints);
      }

      @Override
      public RestClientResponseException createException() throws IOException {
         return StatusHandler.createException(this, DefaultRestClient.this.messageConverters);
      }
   }

   private class DefaultRequestBodyUriSpec implements RestClient.RequestBodyUriSpec {
      private static final Object NO_VERSION = new Object();
      private final HttpMethod httpMethod;
      private @Nullable URI uri;
      private @Nullable HttpHeaders headers;
      private @Nullable MultiValueMap<String, String> cookies;
      private @Nullable Object apiVersion;
      private DefaultRestClient.DefaultRequestBodyUriSpec.@Nullable InternalBody body;
      private @Nullable Map<String, Object> attributes;
      private @Nullable Consumer<ClientHttpRequest> httpRequestConsumer;
      private @Nullable Map<String, Object> hints;

      public DefaultRequestBodyUriSpec(HttpMethod httpMethod) {
         this.httpMethod = httpMethod;
      }

      public RestClient.RequestBodySpec uri(String uriTemplate, @Nullable Object... uriVariables) {
         UriBuilder uriBuilder = DefaultRestClient.this.uriBuilderFactory.uriString(uriTemplate);
         this.attribute(DefaultRestClient.URI_TEMPLATE_ATTRIBUTE, uriBuilder.toUriString());
         return this.uri(DefaultRestClient.this.uriBuilderFactory.expand(uriTemplate, uriVariables));
      }

      public RestClient.RequestBodySpec uri(String uriTemplate, Map<String, ?> uriVariables) {
         UriBuilder uriBuilder = DefaultRestClient.this.uriBuilderFactory.uriString(uriTemplate);
         this.attribute(DefaultRestClient.URI_TEMPLATE_ATTRIBUTE, uriBuilder.toUriString());
         return this.uri(DefaultRestClient.this.uriBuilderFactory.expand(uriTemplate, (Map<String, ? extends Object>)uriVariables));
      }

      public RestClient.RequestBodySpec uri(String uriTemplate, Function<UriBuilder, URI> uriFunction) {
         UriBuilder uriBuilder = DefaultRestClient.this.uriBuilderFactory.uriString(uriTemplate);
         this.attribute(DefaultRestClient.URI_TEMPLATE_ATTRIBUTE, uriBuilder.toUriString());
         return this.uri(uriFunction.apply(DefaultRestClient.this.uriBuilderFactory.uriString(uriTemplate)));
      }

      public RestClient.RequestBodySpec uri(Function<UriBuilder, URI> uriFunction) {
         return this.uri(uriFunction.apply(DefaultRestClient.this.uriBuilderFactory.builder()));
      }

      public RestClient.RequestBodySpec uri(URI uri) {
         if (uri.isAbsolute()) {
            this.uri = uri;
         } else {
            URI baseUri = DefaultRestClient.this.uriBuilderFactory.expand("");
            this.uri = baseUri.resolve(uri);
         }

         return this;
      }

      private HttpHeaders getHeaders() {
         if (this.headers == null) {
            this.headers = new HttpHeaders();
         }

         return this.headers;
      }

      private MultiValueMap<String, String> getCookies() {
         if (this.cookies == null) {
            this.cookies = new LinkedMultiValueMap(3);
         }

         return this.cookies;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec header(String headerName, String... headerValues) {
         for (String headerValue : headerValues) {
            this.getHeaders().add(headerName, headerValue);
         }

         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec headers(Consumer<HttpHeaders> headersConsumer) {
         headersConsumer.accept(this.getHeaders());
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec accept(MediaType... acceptableMediaTypes) {
         this.getHeaders().setAccept(Arrays.asList(acceptableMediaTypes));
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec acceptCharset(Charset... acceptableCharsets) {
         this.getHeaders().setAcceptCharset(Arrays.asList(acceptableCharsets));
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec cookie(String name, String value) {
         this.getCookies().add(name, value);
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec cookies(Consumer<MultiValueMap<String, String>> cookiesConsumer) {
         cookiesConsumer.accept(this.getCookies());
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec contentType(MediaType contentType) {
         this.getHeaders().setContentType(contentType);
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec contentLength(long contentLength) {
         this.getHeaders().setContentLength(contentLength);
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec ifModifiedSince(ZonedDateTime ifModifiedSince) {
         this.getHeaders().setIfModifiedSince(ifModifiedSince);
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec ifNoneMatch(String... ifNoneMatches) {
         this.getHeaders().setIfNoneMatch(Arrays.asList(ifNoneMatches));
         return this;
      }

      public RestClient.RequestBodySpec apiVersion(@Nullable Object version) {
         this.apiVersion = version != null ? version : NO_VERSION;
         return this;
      }

      public RestClient.RequestBodySpec attribute(String name, Object value) {
         this.getAttributes().put(name, value);
         return this;
      }

      public RestClient.RequestBodySpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
         attributesConsumer.accept(this.getAttributes());
         return this;
      }

      private Map<String, Object> getAttributes() {
         Map<String, Object> attributes = this.attributes;
         if (attributes == null) {
            attributes = new ConcurrentHashMap<>(4);
            this.attributes = attributes;
         }

         return attributes;
      }

      public RestClient.RequestBodySpec httpRequest(Consumer<ClientHttpRequest> requestConsumer) {
         this.httpRequestConsumer = this.httpRequestConsumer != null ? this.httpRequestConsumer.andThen(requestConsumer) : requestConsumer;
         return this;
      }

      @Override
      public RestClient.RequestBodySpec body(Object body) {
         this.body = clientHttpRequest -> this.writeWithMessageConverters(body, body.getClass(), clientHttpRequest);
         return this;
      }

      @Override
      public <T> RestClient.RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType) {
         this.body = clientHttpRequest -> this.writeWithMessageConverters(body, bodyType.getType(), clientHttpRequest);
         return this;
      }

      @Override
      public RestClient.RequestBodySpec body(StreamingHttpOutputMessage.Body body) {
         this.body = request -> {
            if (request instanceof StreamingHttpOutputMessage streamingMessage) {
               streamingMessage.setBody(body);
            } else {
               body.writeTo(request.getBody());
            }
         };
         return this;
      }

      public DefaultRestClient.DefaultRequestBodyUriSpec hint(String key, Object value) {
         this.getHints().put(key, value);
         return this;
      }

      private Map<String, Object> getHints() {
         Map<String, Object> hints = this.hints;
         if (hints == null) {
            hints = new ConcurrentHashMap<>(1);
            this.hints = hints;
         }

         return hints;
      }

      private void writeWithMessageConverters(Object body, Type bodyType, ClientHttpRequest clientRequest) throws IOException {
         MediaType contentType = clientRequest.getHeaders().getContentType();
         Class<?> bodyClass = body.getClass();

         for (HttpMessageConverter messageConverter : DefaultRestClient.this.messageConverters) {
            if (messageConverter instanceof GenericHttpMessageConverter genericMessageConverter) {
               if (genericMessageConverter.canWrite(bodyType, bodyClass, contentType)) {
                  this.logBody(body, contentType, genericMessageConverter);
                  genericMessageConverter.write(body, bodyType, contentType, clientRequest);
                  return;
               }
            } else if (messageConverter instanceof SmartHttpMessageConverter smartMessageConverter) {
               ResolvableType resolvableType = ResolvableType.forType(bodyType);
               if (smartMessageConverter.canWrite(resolvableType, bodyClass, contentType)) {
                  this.logBody(body, contentType, smartMessageConverter);
                  smartMessageConverter.write(body, resolvableType, contentType, clientRequest, this.hints);
                  return;
               }
            } else if (messageConverter.canWrite(bodyClass, contentType)) {
               this.logBody(body, contentType, messageConverter);
               messageConverter.write(body, contentType, clientRequest);
               return;
            }
         }

         String message = "No HttpMessageConverter for " + bodyClass.getName();
         if (contentType != null) {
            message = message + " and content type \"" + contentType + "\"";
         }

         throw new RestClientException(message);
      }

      private void logBody(Object body, @Nullable MediaType mediaType, HttpMessageConverter<?> converter) {
         if (DefaultRestClient.logger.isDebugEnabled()) {
            StringBuilder msg = new StringBuilder("Writing [");
            msg.append(body);
            msg.append("] ");
            if (mediaType != null) {
               msg.append("as \"");
               msg.append(mediaType);
               msg.append("\" ");
            }

            msg.append("with ");
            msg.append(converter.getClass().getName());
            DefaultRestClient.logger.debug(msg.toString());
         }
      }

      @Override
      public RestClient.ResponseSpec retrieve() {
         return DefaultRestClient.this.new DefaultResponseSpec(this);
      }

      @Override
      public <T> T exchange(RestClient.RequestHeadersSpec.ExchangeFunction<T> exchangeFunction, boolean close) {
         return this.exchangeInternal(exchangeFunction, close);
      }

      @Override
      public <T> T exchangeForRequiredValue(RestClient.RequestHeadersSpec.RequiredValueExchangeFunction<T> exchangeFunction, boolean close) {
         T value = this.exchangeInternal(exchangeFunction, close);
         Assert.state(value != null, "The exchanged value must not be null");
         return value;
      }

      private <T> T exchangeInternal(RestClient.RequestHeadersSpec.ExchangeFunction<T> exchangeFunction, boolean close) {
         Assert.notNull(exchangeFunction, "ExchangeFunction must not be null");
         ClientHttpResponse clientResponse = null;
         Observation observation = null;
         Scope observationScope = null;
         URI uri = null;

         try {
            uri = this.initUri();
            String serializedCookies = this.serializeCookies();
            if (serializedCookies != null) {
               this.getHeaders().set("Cookie", serializedCookies);
            }

            HttpHeaders headers = this.initHeaders();
            ClientHttpRequest clientRequest = this.createRequest(uri);
            if (headers != null) {
               clientRequest.getHeaders().addAll(headers);
            }

            Map<String, Object> attributes = this.getAttributes();
            clientRequest.getAttributes().putAll(attributes);
            ClientRequestObservationContext observationContext = new ClientRequestObservationContext(clientRequest);
            observationContext.setUriTemplate((String)attributes.get(DefaultRestClient.URI_TEMPLATE_ATTRIBUTE));
            observation = ClientHttpObservationDocumentation.HTTP_CLIENT_EXCHANGES
               .observation(
                  DefaultRestClient.this.observationConvention,
                  DefaultRestClient.DEFAULT_OBSERVATION_CONVENTION,
                  () -> observationContext,
                  DefaultRestClient.this.observationRegistry
               )
               .start();
            observationScope = observation.openScope();
            if (this.body != null) {
               this.body.writeTo(clientRequest);
            }

            if (this.httpRequestConsumer != null) {
               this.httpRequestConsumer.accept(clientRequest);
            }

            clientResponse = clientRequest.execute();
            observationContext.setResponse(clientResponse);
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse convertibleWrapper = DefaultRestClient.this.new DefaultConvertibleClientHttpResponse(
               clientResponse, this.hints
            );
            T result = exchangeFunction.exchange(clientRequest, convertibleWrapper);
            if (close && isStreamingResult(result)) {
               close = false;
            }

            return result;
         } catch (IOException ex) {
            ResourceAccessException resourceAccessException = createResourceAccessException(uri, this.httpMethod, ex);
            if (observation != null) {
               observation.error(resourceAccessException);
            }

            throw resourceAccessException;
         } catch (Throwable error) {
            if (observation != null) {
               observation.error(error);
            }

            throw error;
         } finally {
            if (observationScope != null) {
               observationScope.close();
            }

            if (observation != null) {
               observation.stop();
            }

            if (close && clientResponse != null) {
               clientResponse.close();
            }
         }
      }

      private URI initUri() {
         URI uriToUse = this.uri != null ? this.uri : DefaultRestClient.this.uriBuilderFactory.expand("");
         Object version = this.getApiVersionOrDefault();
         if (version != null) {
            Assert.state(DefaultRestClient.this.apiVersionInserter != null, "No ApiVersionInserter configured");
            uriToUse = DefaultRestClient.this.apiVersionInserter.insertVersion(version, uriToUse);
         }

         return uriToUse;
      }

      private @Nullable Object getApiVersionOrDefault() {
         if (this.apiVersion == null) {
            return DefaultRestClient.this.defaultApiVersion;
         } else {
            return this.apiVersion == NO_VERSION ? null : this.apiVersion;
         }
      }

      private @Nullable String serializeCookies() {
         MultiValueMap<String, String> defaultCookies = DefaultRestClient.this.defaultCookies;
         MultiValueMap<String, String> map;
         if (CollectionUtils.isEmpty(this.cookies)) {
            map = defaultCookies;
         } else if (CollectionUtils.isEmpty(defaultCookies)) {
            map = this.cookies;
         } else {
            map = new LinkedMultiValueMap(defaultCookies.size() + this.cookies.size());
            map.putAll(defaultCookies);
            map.putAll(this.cookies);
         }

         return !CollectionUtils.isEmpty(map) ? serializeCookies(map) : null;
      }

      private static String serializeCookies(MultiValueMap<String, String> map) {
         boolean first = true;
         StringBuilder sb = new StringBuilder();

         for (Entry<String, List<String>> entry : map.entrySet()) {
            for (String value : entry.getValue()) {
               if (!first) {
                  sb.append("; ");
               } else {
                  first = false;
               }

               sb.append(entry.getKey()).append("=").append(value);
            }
         }

         return sb.toString();
      }

      private @Nullable HttpHeaders initHeaders() {
         HttpHeaders defaultHeaders = DefaultRestClient.this.defaultHeaders;
         Object version = this.getApiVersionOrDefault();
         if (version == null) {
            if (this.headers == null || this.headers.isEmpty()) {
               return defaultHeaders;
            }

            if (defaultHeaders == null || defaultHeaders.isEmpty()) {
               return this.headers;
            }
         }

         HttpHeaders result = new HttpHeaders();
         if (defaultHeaders != null) {
            result.putAll(defaultHeaders);
         }

         if (this.headers != null) {
            result.putAll(this.headers);
         }

         if (version != null) {
            Assert.state(DefaultRestClient.this.apiVersionInserter != null, "No ApiVersionInserter configured");
            DefaultRestClient.this.apiVersionInserter.insertVersion(version, result);
         }

         return result;
      }

      private ClientHttpRequest createRequest(URI uri) throws IOException {
         ClientHttpRequestFactory factory;
         if (DefaultRestClient.this.interceptors != null) {
            factory = DefaultRestClient.this.interceptingRequestFactory;
            if (factory == null) {
               factory = new InterceptingClientHttpRequestFactory(
                  DefaultRestClient.this.clientRequestFactory, DefaultRestClient.this.interceptors, DefaultRestClient.this.bufferingPredicate
               );
               DefaultRestClient.this.interceptingRequestFactory = factory;
            }
         } else if (DefaultRestClient.this.bufferingPredicate != null) {
            factory = new BufferingClientHttpRequestFactory(DefaultRestClient.this.clientRequestFactory, DefaultRestClient.this.bufferingPredicate);
         } else {
            factory = DefaultRestClient.this.clientRequestFactory;
         }

         ClientHttpRequest request = factory.createRequest(uri, this.httpMethod);
         if (DefaultRestClient.this.initializers != null) {
            DefaultRestClient.this.initializers.forEach(initializer -> initializer.initialize(request));
         }

         return request;
      }

      private static boolean isStreamingResult(@Nullable Object result) {
         if (result instanceof ResponseEntity<?> entity) {
            result = entity.getBody();
         }

         return result instanceof InputStream || result instanceof InputStreamResource;
      }

      private static ResourceAccessException createResourceAccessException(URI url, HttpMethod method, IOException ex) {
         StringBuilder msg = new StringBuilder("I/O error on ");
         msg.append(method.name());
         msg.append(" request for \"");
         String urlString = url.toString();
         int idx = urlString.indexOf(63);
         if (idx != -1) {
            msg.append(urlString, 0, idx);
         } else {
            msg.append(urlString);
         }

         msg.append("\": ");
         msg.append(ex.getMessage());
         return new ResourceAccessException(msg.toString(), ex);
      }

      @FunctionalInterface
      private interface InternalBody {
         void writeTo(ClientHttpRequest request) throws IOException;
      }
   }

   private class DefaultResponseSpec implements RestClient.ResponseSpec {
      private final RestClient.RequestHeadersSpec<?> requestHeadersSpec;
      private final List<StatusHandler> statusHandlers = new ArrayList<>(1);
      private final int defaultStatusHandlerCount;
      private @Nullable Map<String, Object> hints;

      DefaultResponseSpec(RestClient.RequestHeadersSpec<?> requestHeadersSpec) {
         this.requestHeadersSpec = requestHeadersSpec;
         this.statusHandlers.addAll(DefaultRestClient.this.defaultStatusHandlers);
         this.statusHandlers.add(StatusHandler.createDefaultStatusHandler(DefaultRestClient.this.messageConverters));
         this.defaultStatusHandlerCount = this.statusHandlers.size();
      }

      @Override
      public RestClient.ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate, RestClient.ResponseSpec.ErrorHandler errorHandler) {
         Assert.notNull(statusPredicate, "StatusPredicate must not be null");
         Assert.notNull(errorHandler, "ErrorHandler must not be null");
         return this.onStatusInternal(StatusHandler.of(statusPredicate, errorHandler));
      }

      @Override
      public RestClient.ResponseSpec onStatus(ResponseErrorHandler errorHandler) {
         Assert.notNull(errorHandler, "ResponseErrorHandler must not be null");
         return this.onStatusInternal(StatusHandler.fromErrorHandler(errorHandler));
      }

      private RestClient.ResponseSpec onStatusInternal(StatusHandler statusHandler) {
         Assert.notNull(statusHandler, "StatusHandler must not be null");
         int index = this.statusHandlers.size() - this.defaultStatusHandlerCount;
         this.statusHandlers.add(index, statusHandler);
         return this;
      }

      @Override
      public <T> @Nullable T body(Class<T> bodyType) {
         return this.executeAndExtract((request, response) -> this.readBody(request, response, bodyType, bodyType, this.hints));
      }

      @Override
      public <T> T requiredBody(Class<T> bodyType) {
         T body = this.body(bodyType);
         Assert.state(body != null, "The body must not be null");
         return body;
      }

      @Override
      public <T> @Nullable T body(ParameterizedTypeReference<T> bodyType) {
         Type type = bodyType.getType();
         Class<T> bodyClass = DefaultRestClient.bodyClass(type);
         return this.executeAndExtract((request, response) -> this.readBody(request, response, type, bodyClass, this.hints));
      }

      @Override
      public <T> T requiredBody(ParameterizedTypeReference<T> bodyType) {
         T body = this.body(bodyType);
         Assert.state(body != null, "The body must not be null");
         return body;
      }

      @Override
      public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
         return this.toEntityInternal(bodyType, bodyType);
      }

      @Override
      public <T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType) {
         Type type = bodyType.getType();
         Class<T> bodyClass = DefaultRestClient.bodyClass(type);
         return this.toEntityInternal(type, bodyClass);
      }

      private <T> ResponseEntity<T> toEntityInternal(Type bodyType, Class<T> bodyClass) {
         ResponseEntity<T> entity = this.executeAndExtract((request, response) -> {
            T body = this.readBody(request, response, bodyType, bodyClass, this.hints);

            try {
               return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(body);
            } catch (IOException ex) {
               throw new ResourceAccessException("Could not retrieve response status code: " + ex.getMessage(), ex);
            }
         });
         Assert.state(entity != null, "No ResponseEntity");
         return entity;
      }

      @Override
      public ResponseEntity<Void> toBodilessEntity() {
         ResponseEntity<Void> entity = this.executeAndExtract((request, response) -> {
            try (response) {
               this.applyStatusHandlers(request, response);
               return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).build();
            } catch (UncheckedIOException ex) {
               throw new ResourceAccessException("Could not retrieve response status code: " + ex.getMessage(), ex.getCause());
            } catch (IOException ex) {
               throw new ResourceAccessException("Could not retrieve response status code: " + ex.getMessage(), ex);
            }
         });
         Assert.state(entity != null, "No ResponseEntity");
         return entity;
      }

      @Override
      public RestClient.ResponseSpec hint(String key, Object value) {
         this.getHints().put(key, value);
         return this;
      }

      private Map<String, Object> getHints() {
         Map<String, Object> hints = this.hints;
         if (hints == null) {
            hints = new ConcurrentHashMap<>(1);
            this.hints = hints;
         }

         return hints;
      }

      public <T> T executeAndExtract(RestClient.RequestHeadersSpec.ExchangeFunction<T> exchangeFunction) {
         return this.requestHeadersSpec.exchange(exchangeFunction);
      }

      private <T> @Nullable T readBody(HttpRequest request, ClientHttpResponse response, Type bodyType, Class<T> bodyClass, @Nullable Map<String, Object> hints) {
         return DefaultRestClient.this.readWithMessageConverters(response, () -> this.applyStatusHandlers(request, response), bodyType, bodyClass, hints);
      }

      private void applyStatusHandlers(HttpRequest request, ClientHttpResponse response) {
         try {
            if (response instanceof DefaultRestClient.DefaultConvertibleClientHttpResponse convertibleResponse) {
               response = convertibleResponse.delegate;
            }

            for (StatusHandler handler : this.statusHandlers) {
               if (handler.test(response)) {
                  handler.handle(request, response);
                  return;
               }
            }
         } catch (IOException ex) {
            throw new UncheckedIOException(ex);
         }
      }
   }
}
