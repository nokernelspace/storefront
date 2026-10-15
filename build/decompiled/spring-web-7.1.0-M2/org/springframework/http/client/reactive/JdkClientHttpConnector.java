package org.springframework.http.client.reactive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public class JdkClientHttpConnector implements ClientHttpConnector {
   private final HttpClient httpClient;
   private DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
   private @Nullable Duration readTimeout;
   private ResponseCookie.Parser cookieParser = new JdkResponseCookieParser();

   public JdkClientHttpConnector() {
      this(HttpClient.newHttpClient());
   }

   public JdkClientHttpConnector(HttpClient httpClient) {
      this.httpClient = httpClient;
   }

   public JdkClientHttpConnector(Builder clientBuilder, @Nullable JdkHttpClientResourceFactory resourceFactory) {
      if (resourceFactory != null) {
         Executor executor = resourceFactory.getExecutor();
         clientBuilder.executor(executor);
      }

      this.httpClient = clientBuilder.build();
   }

   public void setBufferFactory(DataBufferFactory bufferFactory) {
      Assert.notNull(bufferFactory, "DataBufferFactory is required");
      this.bufferFactory = bufferFactory;
   }

   public void setReadTimeout(Duration readTimeout) {
      Assert.notNull(readTimeout, "readTimeout is required");
      this.readTimeout = readTimeout;
   }

   public void setCookieParser(ResponseCookie.Parser parser) {
      Assert.notNull(parser, "ResponseCookie parser is required");
      this.cookieParser = parser;
   }

   @Override
   public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
      JdkClientHttpRequest request = new JdkClientHttpRequest(method, uri, this.bufferFactory, this.readTimeout);
      return requestCallback.apply(request).then(Mono.defer(() -> {
         HttpRequest nativeRequest = request.getNativeRequest();
         CompletableFuture<HttpResponse<Publisher<List<ByteBuffer>>>> future = this.httpClient.sendAsync(nativeRequest, BodyHandlers.ofPublisher());
         return Mono.fromCompletionStage(future).map(response -> new JdkClientHttpResponse(response, this.bufferFactory, this.parseCookies(response)));
      }));
   }

   private MultiValueMap<String, ResponseCookie> parseCookies(HttpResponse<?> response) {
      List<String> headers = response.headers().allValues("Set-Cookie");
      return this.cookieParser.parse(headers);
   }
}
