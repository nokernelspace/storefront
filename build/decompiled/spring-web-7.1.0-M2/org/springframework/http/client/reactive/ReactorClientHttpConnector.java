package org.springframework.http.client.reactive;

import io.netty.util.AttributeKey;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClient.RequestSender;

public class ReactorClientHttpConnector implements ClientHttpConnector, SmartLifecycle {
   public static final AttributeKey<Map<String, Object>> ATTRIBUTES_KEY = AttributeKey.valueOf(ReactorClientHttpRequest.class.getName() + ".ATTRIBUTES");
   private static final Log logger = LogFactory.getLog(ReactorClientHttpConnector.class);
   private static final Function<HttpClient, HttpClient> defaultInitializer = client -> client.compress(true);
   private final @Nullable ReactorResourceFactory resourceFactory;
   private final @Nullable Function<HttpClient, HttpClient> mapper;
   private volatile @Nullable HttpClient httpClient;
   private boolean lazyStart = false;
   private final Object lifecycleMonitor = new Object();

   public ReactorClientHttpConnector() {
      this.httpClient = defaultInitializer.apply(HttpClient.create());
      this.resourceFactory = null;
      this.mapper = null;
   }

   public ReactorClientHttpConnector(HttpClient httpClient) {
      Assert.notNull(httpClient, "HttpClient is required");
      this.httpClient = httpClient;
      this.resourceFactory = null;
      this.mapper = null;
   }

   public ReactorClientHttpConnector(ReactorResourceFactory resourceFactory, Function<HttpClient, HttpClient> mapper) {
      this.resourceFactory = resourceFactory;
      this.mapper = mapper;
      if (resourceFactory.isRunning()) {
         this.httpClient = createHttpClient(resourceFactory, mapper);
      } else {
         this.lazyStart = true;
      }
   }

   private static HttpClient createHttpClient(ReactorResourceFactory factory, Function<HttpClient, HttpClient> mapper) {
      return defaultInitializer.andThen(mapper)
         .andThen(httpClient -> (HttpClient)httpClient.runOn(factory.getLoopResources()))
         .apply(HttpClient.create(factory.getConnectionProvider()));
   }

   @Override
   public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri, Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
      HttpClient httpClient = this.httpClient;
      if (httpClient == null) {
         Assert.state(this.resourceFactory != null && this.mapper != null, "Illegal configuration");
         if (this.resourceFactory.isRunning()) {
            synchronized (this.lifecycleMonitor) {
               httpClient = this.httpClient;
               if (httpClient == null && this.lazyStart) {
                  httpClient = createHttpClient(this.resourceFactory, this.mapper);
                  this.httpClient = httpClient;
                  this.lazyStart = false;
               }
            }
         }

         if (httpClient == null) {
            httpClient = createHttpClient(this.resourceFactory, this.mapper);
         }
      }

      RequestSender requestSender = httpClient.request(io.netty.handler.codec.http.HttpMethod.valueOf(method.name()));
      requestSender = setUri(requestSender, uri);
      AtomicReference<ReactorClientHttpResponse> responseRef = new AtomicReference<>();
      return requestSender.send((request, outbound) -> (Publisher)requestCallback.apply(this.adaptRequest(method, uri, request, outbound)))
         .responseConnection((response, connection) -> {
            ReactorClientHttpResponse clientResponse = new ReactorClientHttpResponse(response, connection);
            responseRef.set(clientResponse);
            registerAttributeCallback(connection);
            return Mono.just(clientResponse);
         })
         .next()
         .doOnCancel(() -> {
            ReactorClientHttpResponse response = responseRef.get();
            if (response != null) {
               response.releaseAfterCancel(method);
            }
         });
   }

   private static RequestSender setUri(RequestSender requestSender, URI uri) {
      if (uri.isAbsolute()) {
         try {
            return (RequestSender)requestSender.uri(uri);
         } catch (Exception var3) {
         }
      }

      return (RequestSender)requestSender.uri(uri.toString());
   }

   private ReactorClientHttpRequest adaptRequest(HttpMethod method, URI uri, HttpClientRequest request, NettyOutbound nettyOutbound) {
      return new ReactorClientHttpRequest(method, uri, request, nettyOutbound);
   }

   private static void registerAttributeCallback(Connection connection) {
      connection.onTerminate().subscribe(aVoid -> {}, ex -> clearAttribute(connection), () -> clearAttribute(connection));
   }

   private static void clearAttribute(Connection connection) {
      connection.channel().attr(ATTRIBUTES_KEY).set(null);
   }

   public void start() {
      if (this.resourceFactory != null && this.mapper != null) {
         synchronized (this.lifecycleMonitor) {
            if (this.httpClient == null) {
               this.httpClient = createHttpClient(this.resourceFactory, this.mapper);
               this.lazyStart = false;
            }
         }
      } else {
         logger.warn("Restarting a ReactorClientHttpConnector bean is only supported with externally managed Reactor Netty resources");
      }
   }

   public void stop() {
      if (this.resourceFactory != null && this.mapper != null) {
         synchronized (this.lifecycleMonitor) {
            this.httpClient = null;
            this.lazyStart = false;
         }
      }
   }

   public boolean isRunning() {
      return this.httpClient != null;
   }

   public int getPhase() {
      return 1;
   }
}
