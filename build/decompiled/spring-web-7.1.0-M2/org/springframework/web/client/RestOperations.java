package org.springframework.web.client;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

@Deprecated(since = "7.1", forRemoval = true)
public interface RestOperations {
   <T> @Nullable T getForObject(String url, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException;

   <T> @Nullable T getForObject(String url, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   <T> @Nullable T getForObject(URI url, Class<T> responseType) throws RestClientException;

   <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException;

   <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) throws RestClientException;

   HttpHeaders headForHeaders(String url, @Nullable Object... uriVariables) throws RestClientException;

   HttpHeaders headForHeaders(String url, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   HttpHeaders headForHeaders(URI url) throws RestClientException;

   @Nullable URI postForLocation(String url, @Nullable Object request, @Nullable Object... uriVariables) throws RestClientException;

   @Nullable URI postForLocation(String url, @Nullable Object request, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   @Nullable URI postForLocation(URI url, @Nullable Object request) throws RestClientException;

   <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException;

   <T> @Nullable T postForObject(String url, @Nullable Object request, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   <T> @Nullable T postForObject(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException;

   <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException;

   <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   <T> ResponseEntity<T> postForEntity(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException;

   void put(String url, @Nullable Object request, @Nullable Object... uriVariables) throws RestClientException;

   void put(String url, @Nullable Object request, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   void put(URI url, @Nullable Object request) throws RestClientException;

   <T> @Nullable T patchForObject(String url, @Nullable Object request, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException;

   <T> @Nullable T patchForObject(String url, @Nullable Object request, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   <T> @Nullable T patchForObject(URI url, @Nullable Object request, Class<T> responseType) throws RestClientException;

   void delete(String url, @Nullable Object... uriVariables) throws RestClientException;

   void delete(String url, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   void delete(URI url) throws RestClientException;

   Set<HttpMethod> optionsForAllow(String url, @Nullable Object... uriVariables) throws RestClientException;

   Set<HttpMethod> optionsForAllow(String url, Map<String, ? extends @Nullable Object> uriVariables) throws RestClientException;

   Set<HttpMethod> optionsForAllow(URI url) throws RestClientException;

   <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType, @Nullable Object... uriVariables) throws RestClientException;

   <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ? extends @Nullable Object> uriVariables
   ) throws RestClientException;

   <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType) throws RestClientException;

   <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, @Nullable Object... uriVariables
   ) throws RestClientException;

   <T> ResponseEntity<T> exchange(
      String url,
      HttpMethod method,
      @Nullable HttpEntity<?> requestEntity,
      ParameterizedTypeReference<T> responseType,
      Map<String, ? extends @Nullable Object> uriVariables
   ) throws RestClientException;

   <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) throws RestClientException;

   <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, Class<T> responseType) throws RestClientException;

   <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) throws RestClientException;

   <T> @Nullable T execute(
      String uriTemplate,
      HttpMethod method,
      @Nullable RequestCallback requestCallback,
      @Nullable ResponseExtractor<T> responseExtractor,
      @Nullable Object... uriVariables
   ) throws RestClientException;

   <T> @Nullable T execute(
      String uriTemplate,
      HttpMethod method,
      @Nullable RequestCallback requestCallback,
      @Nullable ResponseExtractor<T> responseExtractor,
      Map<String, ? extends @Nullable Object> uriVariables
   ) throws RestClientException;

   <T> @Nullable T execute(URI url, HttpMethod method, @Nullable RequestCallback requestCallback, @Nullable ResponseExtractor<T> responseExtractor) throws RestClientException;
}
