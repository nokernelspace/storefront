package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.BiPredicate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;

class InterceptingClientHttpRequest extends AbstractBufferingClientHttpRequest {
   private final ClientHttpRequestFactory requestFactory;
   private final List<ClientHttpRequestInterceptor> interceptors;
   private final HttpMethod method;
   private final URI uri;
   private final BiPredicate<URI, HttpMethod> bufferingPredicate;

   protected InterceptingClientHttpRequest(
      ClientHttpRequestFactory requestFactory,
      List<ClientHttpRequestInterceptor> interceptors,
      URI uri,
      HttpMethod method,
      BiPredicate<URI, HttpMethod> bufferingPredicate
   ) {
      this.requestFactory = requestFactory;
      this.interceptors = interceptors;
      this.method = method;
      this.uri = uri;
      this.bufferingPredicate = bufferingPredicate;
   }

   @Override
   public HttpMethod getMethod() {
      return this.method;
   }

   @Override
   public URI getURI() {
      return this.uri;
   }

   @Override
   protected final ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException {
      return this.getExecution().execute(this, bufferedOutput);
   }

   private ClientHttpRequestExecution getExecution() {
      ClientHttpRequestExecution execution = new InterceptingClientHttpRequest.EndOfChainRequestExecution(this.requestFactory);
      return this.interceptors.stream().reduce(ClientHttpRequestInterceptor::andThen).map(interceptor -> interceptor.apply(execution)).orElse(execution);
   }

   private boolean shouldBufferResponse(HttpRequest request) {
      return this.bufferingPredicate.test(request.getURI(), request.getMethod());
   }

   private class EndOfChainRequestExecution implements ClientHttpRequestExecution {
      private final ClientHttpRequestFactory requestFactory;

      public EndOfChainRequestExecution(ClientHttpRequestFactory requestFactory) {
         this.requestFactory = requestFactory;
      }

      @Override
      public ClientHttpResponse execute(HttpRequest request, byte[] body) throws IOException {
         ClientHttpRequest delegate = this.requestFactory.createRequest(request.getURI(), request.getMethod());
         request.getHeaders().forEach((key, value) -> delegate.getHeaders().addAll(key, (List<? extends String>)value));
         request.getAttributes().forEach((key, value) -> delegate.getAttributes().put(key, value));
         return InterceptingClientHttpRequest.this.executeWithRequestAndBody(delegate, body, InterceptingClientHttpRequest.this.shouldBufferResponse(request));
      }
   }
}
