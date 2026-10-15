package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.reactive.client.ReactiveResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.JettyDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class JettyClientHttpConnector implements ClientHttpConnector {
   private final HttpClient httpClient;
   private JettyDataBufferFactory bufferFactory = new JettyDataBufferFactory();
   private ResponseCookie.Parser cookieParser = new JdkResponseCookieParser();

   public JettyClientHttpConnector() {
      this(new HttpClient());
   }

   public JettyClientHttpConnector(HttpClient httpClient) {
      this(httpClient, null);
   }

   public JettyClientHttpConnector(HttpClient httpClient, @Nullable JettyResourceFactory resourceFactory) {
      Assert.notNull(httpClient, "HttpClient is required");
      if (resourceFactory != null) {
         httpClient.setExecutor(resourceFactory.getExecutor());
         httpClient.setByteBufferPool(resourceFactory.getByteBufferPool());
         httpClient.setScheduler(resourceFactory.getScheduler());
      }

      this.httpClient = httpClient;
   }

   public void setBufferFactory(JettyDataBufferFactory bufferFactory) {
      this.bufferFactory = bufferFactory;
   }

   public void setCookieParser(ResponseCookie.Parser parser) {
      Assert.notNull(parser, "ResponseCookie parser is required");
      this.cookieParser = parser;
   }

   @Override
   public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
      if (!uri.isAbsolute()) {
         return Mono.error(new IllegalArgumentException("URI is not absolute: " + uri));
      }

      if (!this.httpClient.isStarted()) {
         try {
            this.httpClient.start();
         } catch (Exception ex) {
            return Mono.error(ex);
         }
      }

      Request jettyRequest = this.httpClient.newRequest(uri).method(method.toString());
      JettyClientHttpRequest request = new JettyClientHttpRequest(jettyRequest, this.bufferFactory);
      return requestCallback.apply(request).then(this.execute(request));
   }

   private Mono<ClientHttpResponse> execute(JettyClientHttpRequest request) {
      return Mono.fromDirect(request.toReactiveRequest().response((response, chunkPublisher) -> {
         Flux<DataBuffer> content = Flux.from(chunkPublisher).map(this.bufferFactory::wrap);
         return Mono.just(new JettyClientHttpResponse(response, content, this.parseCookies(response)));
      }));
   }

   private MultiValueMap<String, ResponseCookie> parseCookies(ReactiveResponse response) {
      List<String> headers = response.getHeaders().getValuesList("Set-Cookie");
      return this.cookieParser.parse(headers);
   }
}
