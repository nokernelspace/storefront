package org.springframework.web.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.Observation.Scope;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.http.client.support.InterceptingHttpAccessor;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriTemplateHandler;

@Deprecated(since = "7.1", forRemoval = true)
public class RestTemplate extends InterceptingHttpAccessor implements RestOperations {
   private static final ClientRequestObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultClientRequestObservationConvention();
   private final List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
   private ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();
   private UriTemplateHandler uriTemplateHandler;
   private final ResponseExtractor<HttpHeaders> headersExtractor = new RestTemplate.HeadersExtractor();
   private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
   private @Nullable ClientRequestObservationConvention observationConvention;

   public RestTemplate() {
      this(HttpMessageConverters.forClient().registerDefaults().build());
   }

   public RestTemplate(ClientHttpRequestFactory requestFactory) {
      this();
      this.setRequestFactory(requestFactory);
   }

   public RestTemplate(Iterable<HttpMessageConverter<?>> messageConverters) {
      this.validateConverters(messageConverters);
      messageConverters.forEach(this.messageConverters::add);
      this.uriTemplateHandler = initUriTemplateHandler();
      this.updateErrorHandlerConverters();
   }

   private void updateErrorHandlerConverters() {
      if (this.errorHandler instanceof DefaultResponseErrorHandler handler) {
         handler.setMessageConverters(this.messageConverters);
      }
   }

   private static DefaultUriBuilderFactory initUriTemplateHandler() {
      DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory();
      uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
      return uriFactory;
   }

   public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
      this.validateConverters(messageConverters);
      if (this.messageConverters != messageConverters) {
         this.messageConverters.clear();
         this.messageConverters.addAll(messageConverters);
      }

      this.updateErrorHandlerConverters();
   }

   private void validateConverters(Iterable<HttpMessageConverter<?>> messageConverters) {
      Assert.notNull(messageConverters, "At least one HttpMessageConverter is required");
      Assert.isTrue(messageConverters.iterator().hasNext(), "At least one HttpMessageConverter is required");
      messageConverters.forEach(converter -> Assert.notNull(converter, "The HttpMessageConverter list must not contain null elements"));
   }

   public List<HttpMessageConverter<?>> getMessageConverters() {
      return this.messageConverters;
   }

   public void setErrorHandler(ResponseErrorHandler errorHandler) {
      Assert.notNull(errorHandler, "ResponseErrorHandler must not be null");
      this.errorHandler = errorHandler;
      this.updateErrorHandlerConverters();
   }

   public ResponseErrorHandler getErrorHandler() {
      return this.errorHandler;
   }

   public void setDefaultUriVariables(Map<String, ? extends @Nullable Object> uriVars) {
      if (this.uriTemplateHandler instanceof DefaultUriBuilderFactory factory) {
         factory.setDefaultUriVariables(uriVars);
      } else {
         throw new IllegalArgumentException("This property is not supported with the configured UriTemplateHandler.");
      }
   }

   public void setUriTemplateHandler(UriTemplateHandler handler) {
      Assert.notNull(handler, "UriTemplateHandler must not be null");
      this.uriTemplateHandler = handler;
   }

   public UriTemplateHandler getUriTemplateHandler() {
      return this.uriTemplateHandler;
   }

   public void setObservationRegistry(ObservationRegistry observationRegistry) {
      Assert.notNull(observationRegistry, "observationRegistry must not be null");
      this.observationRegistry = observationRegistry;
   }

   public ObservationRegistry getObservationRegistry() {
      return this.observationRegistry;
   }

   public void setObservationConvention(ClientRequestObservationConvention observationConvention) {
      Assert.notNull(observationConvention, "observationConvention must not be null");
      this.observationConvention = observationConvention;
   }

   public @Nullable ClientRequestObservationConvention getObservationConvention() {
      return this.observationConvention;
   }

   @Override
   public <T> @Nullable T getForObject(String url, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.acceptHeaderRequestCallback(responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables);
   }

   @Override
   public <T> @Nullable T getForObject(String url, Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.acceptHeaderRequestCallback(responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.GET, requestCallback, responseExtractor, (Map<String, ? extends Object>)uriVariables);
   }

   @Override
   public <T> @Nullable T getForObject(URI url, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.acceptHeaderRequestCallback(responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.GET, requestCallback, responseExtractor);
   }

   @Override
   public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.acceptHeaderRequestCallback(responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.acceptHeaderRequestCallback(responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.acceptHeaderRequestCallback(responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, HttpMethod.GET, requestCallback, responseExtractor));
   }

   @Override
   public HttpHeaders headForHeaders(String url, @Nullable Object... uriVariables) throws RestClientException {
      return nonNull(this.execute(url, HttpMethod.HEAD, null, this.headersExtractor(), uriVariables));
   }

   @Override
   public HttpHeaders headForHeaders(String url, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      return nonNull(this.execute(url, HttpMethod.HEAD, null, this.headersExtractor(), uriVariables));
   }

   @Override
   public HttpHeaders headForHeaders(URI url) throws RestClientException {
      return nonNull(this.execute(url, HttpMethod.HEAD, null, this.headersExtractor()));
   }

   @Override
   public @Nullable URI postForLocation(String url, @Nullable Object request, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request);
      HttpHeaders headers = this.execute(url, HttpMethod.POST, requestCallback, this.headersExtractor(), uriVariables);
      return headers != null ? headers.getLocation() : null;
   }

   @Override
   public @Nullable URI postForLocation(String url, @Nullable Object request, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request);
      HttpHeaders headers = this.execute(url, HttpMethod.POST, requestCallback, this.headersExtractor(), uriVariables);
      return headers != null ? headers.getLocation() : null;
   }

   @Override
   public @Nullable URI postForLocation(URI url, @Nullable Object request) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request);
      HttpHeaders headers = this.execute(url, HttpMethod.POST, requestCallback, this.headersExtractor());
      return headers != null ? headers.getLocation() : null;
   }

   @Override
   public <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables);
   }

   @Override
   public <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables);
   }

   @Override
   public <T> @Nullable T postForObject(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters());
      return this.execute(url, HttpMethod.POST, requestCallback, responseExtractor);
   }

   @Override
   public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> postForEntity(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, HttpMethod.POST, requestCallback, responseExtractor));
   }

   @Override
   public void put(String url, @Nullable Object request, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request);
      this.execute(url, HttpMethod.PUT, requestCallback, null, uriVariables);
   }

   @Override
   public void put(String url, @Nullable Object request, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request);
      this.execute(url, HttpMethod.PUT, requestCallback, null, uriVariables);
   }

   @Override
   public void put(URI url, @Nullable Object request) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request);
      this.execute(url, HttpMethod.PUT, requestCallback, null);
   }

   @Override
   public <T> @Nullable T patchForObject(String url, @Nullable Object request, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.PATCH, requestCallback, responseExtractor, uriVariables);
   }

   @Override
   public <T> @Nullable T patchForObject(String url, @Nullable Object request, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters(), this.logger);
      return this.execute(url, HttpMethod.PATCH, requestCallback, responseExtractor, uriVariables);
   }

   @Override
   public <T> @Nullable T patchForObject(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(request, responseType);
      HttpMessageConverterExtractor<T> responseExtractor = new HttpMessageConverterExtractor<>(responseType, this.getMessageConverters());
      return this.execute(url, HttpMethod.PATCH, requestCallback, responseExtractor);
   }

   @Override
   public void delete(String url, @Nullable Object... uriVariables) throws RestClientException {
      this.execute(url, HttpMethod.DELETE, null, null, uriVariables);
   }

   @Override
   public void delete(String url, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      this.execute(url, HttpMethod.DELETE, null, null, uriVariables);
   }

   @Override
   public void delete(URI url) throws RestClientException {
      this.execute(url, HttpMethod.DELETE, null, null);
   }

   @Override
   public Set<HttpMethod> optionsForAllow(String url, @Nullable Object... uriVariables) throws RestClientException {
      ResponseExtractor<HttpHeaders> headersExtractor = this.headersExtractor();
      HttpHeaders headers = this.execute(url, HttpMethod.OPTIONS, null, headersExtractor, uriVariables);
      return headers != null ? headers.getAllow() : Collections.emptySet();
   }

   @Override
   public Set<HttpMethod> optionsForAllow(String url, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException {
      ResponseExtractor<HttpHeaders> headersExtractor = this.headersExtractor();
      HttpHeaders headers = this.execute(url, HttpMethod.OPTIONS, null, headersExtractor, uriVariables);
      return headers != null ? headers.getAllow() : Collections.emptySet();
   }

   @Override
   public Set<HttpMethod> optionsForAllow(URI url) throws RestClientException {
      ResponseExtractor<HttpHeaders> headersExtractor = this.headersExtractor();
      HttpHeaders headers = this.execute(url, HttpMethod.OPTIONS, null, headersExtractor);
      return headers != null ? headers.getAllow() : Collections.emptySet();
   }

   @Override
   public <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType, @Nullable Object... uriVariables
   ) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(requestEntity, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, method, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables
   ) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(requestEntity, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, method, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(requestEntity, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.execute(url, method, requestCallback, responseExtractor));
   }

   @Override
   public <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, @Nullable Object... uriVariables
   ) throws RestClientException {
      Type type = responseType.getType();
      RequestCallback requestCallback = this.httpEntityCallback(requestEntity, type);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(type);
      return nonNull(this.execute(url, method, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> exchange(
      String url,
      HttpMethod method,
      @Nullable HttpEntity<?> requestEntity,
      ParameterizedTypeReference<T> responseType,
      Map<String, ? extends @Nullable Object> uriVariables
   ) throws RestClientException {
      Type type = responseType.getType();
      RequestCallback requestCallback = this.httpEntityCallback(requestEntity, type);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(type);
      return nonNull(this.execute(url, method, requestCallback, responseExtractor, uriVariables));
   }

   @Override
   public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) throws RestClientException {
      Type type = responseType.getType();
      RequestCallback requestCallback = this.httpEntityCallback(requestEntity, type);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(type);
      return nonNull(this.execute(url, method, requestCallback, responseExtractor));
   }

   @Override
   public <T> ResponseEntity<T> exchange(RequestEntity<?> entity, Class<T> responseType) throws RestClientException {
      RequestCallback requestCallback = this.httpEntityCallback(entity, responseType);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(responseType);
      return nonNull(this.doExecute(this.resolveUrl(entity), this.resolveUriTemplate(entity), entity.getMethod(), requestCallback, responseExtractor));
   }

   @Override
   public <T> ResponseEntity<T> exchange(RequestEntity<?> entity, ParameterizedTypeReference<T> responseType) throws RestClientException {
      Type type = responseType.getType();
      RequestCallback requestCallback = this.httpEntityCallback(entity, type);
      ResponseExtractor<ResponseEntity<T>> responseExtractor = this.responseEntityExtractor(type);
      return nonNull(this.doExecute(this.resolveUrl(entity), this.resolveUriTemplate(entity), entity.getMethod(), requestCallback, responseExtractor));
   }

   private URI resolveUrl(RequestEntity<?> entity) {
      if (entity instanceof RequestEntity.UriTemplateRequestEntity<?> ext) {
         if (ext.getVars() != null) {
            return this.uriTemplateHandler.expand(ext.getUriTemplate(), ext.getVars());
         } else if (ext.getVarsMap() != null) {
            return this.uriTemplateHandler.expand(ext.getUriTemplate(), ext.getVarsMap());
         } else {
            throw new IllegalStateException("No variables specified for URI template: " + ext.getUriTemplate());
         }
      } else {
         return entity.getUrl();
      }
   }

   private @Nullable String resolveUriTemplate(RequestEntity<?> entity) {
      return entity instanceof RequestEntity.UriTemplateRequestEntity<?> templated ? templated.getUriTemplate() : null;
   }

   @Override
   public <T> @Nullable T execute(
      String uriTemplate,
      HttpMethod method,
      @Nullable RequestCallback requestCallback,
      @Nullable ResponseExtractor<T> responseExtractor,
      @Nullable Object... uriVariables
   ) throws RestClientException {
      URI url = this.getUriTemplateHandler().expand(uriTemplate, uriVariables);
      return this.doExecute(url, uriTemplate, method, requestCallback, responseExtractor);
   }

   @Override
   public <T> @Nullable T execute(
      String uriTemplate,
      HttpMethod method,
      @Nullable RequestCallback requestCallback,
      @Nullable ResponseExtractor<T> responseExtractor,
      Map<String, ? extends @Nullable Object> uriVariables
   ) throws RestClientException {
      URI url = this.getUriTemplateHandler().expand(uriTemplate, uriVariables);
      return this.doExecute(url, uriTemplate, method, requestCallback, responseExtractor);
   }

   @Override
   public <T> @Nullable T execute(URI url, HttpMethod method, @Nullable RequestCallback requestCallback, @Nullable ResponseExtractor<T> responseExtractor) throws RestClientException {
      return this.doExecute(url, null, method, requestCallback, responseExtractor);
   }

   protected <T> @Nullable T doExecute(
      URI url,
      @Nullable String uriTemplate,
      @Nullable HttpMethod method,
      @Nullable RequestCallback requestCallback,
      @Nullable ResponseExtractor<T> responseExtractor
   ) throws RestClientException {
      Assert.notNull(url, "url is required");
      Assert.notNull(method, "HttpMethod is required");

      ClientHttpRequest request;
      try {
         request = this.createRequest(url, method);
      } catch (IOException ex) {
         throw createResourceAccessException(url, method, ex);
      }

      ClientRequestObservationContext observationContext = new ClientRequestObservationContext(request);
      observationContext.setUriTemplate(uriTemplate);
      Observation observation = ClientHttpObservationDocumentation.HTTP_CLIENT_EXCHANGES
         .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext, this.observationRegistry)
         .start();
      ClientHttpResponse response = null;

      ResourceAccessException accessEx;
      try {
         Scope scope = observation.openScope();

         try {
            if (requestCallback != null) {
               requestCallback.doWithRequest(request);
            }

            response = request.execute();
            observationContext.setResponse(response);
            this.handleResponse(url, method, response);
            accessEx = responseExtractor != null ? responseExtractor.extractData(response) : null;
         } catch (Throwable var22) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var20) {
                  var22.addSuppressed(var20);
               }
            }

            throw var22;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (IOException ex) {
         accessEx = createResourceAccessException(url, method, ex);
         observation.error(accessEx);
         throw accessEx;
      } catch (Throwable ex) {
         observation.error(ex);
         throw ex;
      } finally {
         if (response != null) {
            response.close();
         }

         observation.stop();
      }

      return (T)accessEx;
   }

   private static ResourceAccessException createResourceAccessException(URI url, HttpMethod method, IOException ex) {
      String resource = url.toString();
      resource = url.getRawQuery() != null ? resource.substring(0, resource.indexOf(63)) : resource;
      return new ResourceAccessException("I/O error on " + method.name() + " request for \"" + resource + "\": " + ex.getMessage(), ex);
   }

   protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
      ResponseErrorHandler errorHandler = this.getErrorHandler();
      boolean hasError = errorHandler.hasError(response);
      if (this.logger.isDebugEnabled()) {
         try {
            HttpStatusCode statusCode = response.getStatusCode();
            this.logger.debug("Response " + statusCode);
         } catch (IOException ex) {
            this.logger.debug("Failed to obtain response status code", ex);
         }
      }

      if (hasError) {
         errorHandler.handleError(url, method, response);
      }
   }

   public <T> RequestCallback acceptHeaderRequestCallback(Class<T> responseType) {
      return new RestTemplate.AcceptHeaderRequestCallback(responseType);
   }

   public <T> RequestCallback httpEntityCallback(@Nullable Object requestBody) {
      return new RestTemplate.HttpEntityRequestCallback(requestBody);
   }

   public <T> RequestCallback httpEntityCallback(@Nullable Object requestBody, Type responseType) {
      return new RestTemplate.HttpEntityRequestCallback(requestBody, responseType);
   }

   public <T> ResponseExtractor<ResponseEntity<T>> responseEntityExtractor(Type responseType) {
      return new RestTemplate.ResponseEntityResponseExtractor<>(responseType);
   }

   protected ResponseExtractor<HttpHeaders> headersExtractor() {
      return this.headersExtractor;
   }

   private static <T> T nonNull(@Nullable T result) {
      Assert.state(result != null, "No result");
      return result;
   }

   private class AcceptHeaderRequestCallback implements RequestCallback {
      private final @Nullable Type responseType;

      public AcceptHeaderRequestCallback(@Nullable Type responseType) {
         this.responseType = responseType;
      }

      @Override
      public void doWithRequest(ClientHttpRequest request) throws IOException {
         if (this.responseType != null) {
            List<MediaType> allSupportedMediaTypes = RestTemplate.this.getMessageConverters()
               .stream()
               .filter(converter -> this.canReadResponse(this.responseType, (HttpMessageConverter<?>)converter))
               .flatMap(converter -> this.getSupportedMediaTypes(this.responseType, (HttpMessageConverter<?>)converter))
               .distinct()
               .collect(Collectors.toList());
            MimeTypeUtils.sortBySpecificity(allSupportedMediaTypes);
            if (RestTemplate.this.logger.isDebugEnabled()) {
               RestTemplate.this.logger.debug("Accept=" + allSupportedMediaTypes);
            }

            request.getHeaders().setAccept(allSupportedMediaTypes);
         }
      }

      private boolean canReadResponse(Type responseType, HttpMessageConverter<?> converter) {
         if (converter instanceof GenericHttpMessageConverter<?> genericConverter) {
            return genericConverter.canRead(responseType, null, null);
         } else if (converter instanceof SmartHttpMessageConverter<?> smartConverter) {
            return smartConverter.canRead(ResolvableType.forType(responseType), null);
         } else {
            return responseType instanceof Class<?> responseClass ? converter.canRead(responseClass, null) : false;
         }
      }

      private Stream<MediaType> getSupportedMediaTypes(Type type, HttpMessageConverter<?> converter) {
         Class<?> clazz = (type instanceof ParameterizedType parameterizedType ? parameterizedType.getRawType() : type) instanceof Class<?> rawClass
            ? rawClass
            : null;
         return (clazz != null ? converter.getSupportedMediaTypes(clazz) : converter.getSupportedMediaTypes())
            .stream()
            .map(mediaType -> mediaType.getCharset() != null ? new MediaType(mediaType.getType(), mediaType.getSubtype()) : mediaType);
      }
   }

   private static class HeadersExtractor implements ResponseExtractor<HttpHeaders> {
      public HttpHeaders extractData(ClientHttpResponse response) {
         return response.getHeaders();
      }
   }

   private class HttpEntityRequestCallback extends RestTemplate.AcceptHeaderRequestCallback {
      private final HttpEntity<?> requestEntity;

      public HttpEntityRequestCallback(@Nullable Object requestBody) {
         this(requestBody, null);
      }

      public HttpEntityRequestCallback(@Nullable Object requestBody, @Nullable Type responseType) {
         super(responseType);
         if (requestBody instanceof HttpEntity<?> httpEntity) {
            this.requestEntity = httpEntity;
         } else if (requestBody != null) {
            this.requestEntity = new HttpEntity<>(requestBody);
         } else {
            this.requestEntity = HttpEntity.EMPTY;
         }
      }

      @Override
      public void doWithRequest(ClientHttpRequest httpRequest) throws IOException {
         super.doWithRequest(httpRequest);
         Object requestBody = this.requestEntity.getBody();
         if (requestBody == null) {
            HttpHeaders httpHeaders = httpRequest.getHeaders();
            HttpHeaders requestHeaders = this.requestEntity.getHeaders();
            if (!requestHeaders.isEmpty()) {
               requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>((Collection<? extends String>)values)));
            }

            if (httpHeaders.getContentLength() < 0L) {
               httpHeaders.setContentLength(0L);
            }
         } else {
            Class<?> requestBodyClass = requestBody.getClass();
            Type requestBodyType = this.requestEntity instanceof RequestEntity<?> _requestEntity ? _requestEntity.getType() : requestBodyClass;
            HttpHeaders httpHeaders = httpRequest.getHeaders();
            HttpHeaders requestHeaders = this.requestEntity.getHeaders();
            MediaType requestContentType = requestHeaders.getContentType();

            for (HttpMessageConverter<?> messageConverter : RestTemplate.this.getMessageConverters()) {
               if (messageConverter instanceof GenericHttpMessageConverter genericConverter) {
                  if (genericConverter.canWrite(requestBodyType, requestBodyClass, requestContentType)) {
                     if (!requestHeaders.isEmpty()) {
                        requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>((Collection<? extends String>)values)));
                     }

                     this.logBody(requestBody, requestContentType, genericConverter);
                     genericConverter.write(requestBody, requestBodyType, requestContentType, httpRequest);
                     return;
                  }
               } else if (messageConverter instanceof SmartHttpMessageConverter smartConverter) {
                  ResolvableType resolvableType = ResolvableType.forType(requestBodyType);
                  if (smartConverter.canWrite(resolvableType, requestBodyClass, requestContentType)) {
                     if (!requestHeaders.isEmpty()) {
                        requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>((Collection<? extends String>)values)));
                     }

                     this.logBody(requestBody, requestContentType, smartConverter);
                     smartConverter.write(requestBody, resolvableType, requestContentType, httpRequest, null);
                     return;
                  }
               } else if (messageConverter.canWrite(requestBodyClass, requestContentType)) {
                  if (!requestHeaders.isEmpty()) {
                     requestHeaders.forEach((key, values) -> httpHeaders.put(key, new ArrayList<>((Collection<? extends String>)values)));
                  }

                  this.logBody(requestBody, requestContentType, messageConverter);
                  ((HttpMessageConverter<Object>)messageConverter).write(requestBody, requestContentType, httpRequest);
                  return;
               }
            }

            String message = "No HttpMessageConverter for " + requestBodyClass.getName();
            if (requestContentType != null) {
               message = message + " and content type \"" + requestContentType + "\"";
            }

            throw new RestClientException(message);
         }
      }

      private void logBody(Object body, @Nullable MediaType mediaType, HttpMessageConverter<?> converter) {
         if (RestTemplate.this.logger.isDebugEnabled()) {
            if (mediaType != null) {
               RestTemplate.this.logger.debug("Writing [" + body + "] as \"" + mediaType + "\"");
            } else {
               RestTemplate.this.logger.debug("Writing [" + body + "] with " + converter.getClass().getName());
            }
         }
      }
   }

   private class ResponseEntityResponseExtractor<T> implements ResponseExtractor<ResponseEntity<T>> {
      private final @Nullable HttpMessageConverterExtractor<T> delegate;

      public ResponseEntityResponseExtractor(@Nullable Type responseType) {
         if (responseType != null && Void.class != responseType) {
            this.delegate = new HttpMessageConverterExtractor<>(responseType, RestTemplate.this.getMessageConverters(), RestTemplate.this.logger);
         } else {
            this.delegate = null;
         }
      }

      public ResponseEntity<T> extractData(ClientHttpResponse response) throws IOException {
         if (this.delegate != null) {
            T body = this.delegate.extractData(response);
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(body);
         } else {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).build();
         }
      }
   }
}
