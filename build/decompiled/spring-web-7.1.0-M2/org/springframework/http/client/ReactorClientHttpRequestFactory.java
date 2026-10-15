package org.springframework.http.client;

import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import reactor.netty.http.client.HttpClient;

public class ReactorClientHttpRequestFactory implements ClientHttpRequestFactory, SmartLifecycle {
   private static final Log logger = LogFactory.getLog(ReactorClientHttpRequestFactory.class);
   private static final Function<HttpClient, HttpClient> defaultInitializer = client -> (HttpClient)client.compress(true)
      .responseTimeout(Duration.ofSeconds(10L))
      .proxyWithSystemProperties();
   private final @Nullable ReactorResourceFactory resourceFactory;
   private final @Nullable Function<HttpClient, HttpClient> mapper;
   private @Nullable Executor executor;
   private @Nullable Integer connectTimeout;
   private @Nullable Duration readTimeout;
   private @Nullable Duration exchangeTimeout;
   private volatile @Nullable HttpClient httpClient;
   private final Object lifecycleMonitor = new Object();

   public ReactorClientHttpRequestFactory() {
      this(defaultInitializer.apply(HttpClient.create()));
   }

   public ReactorClientHttpRequestFactory(HttpClient client) {
      Assert.notNull(client, "HttpClient must not be null");
      this.resourceFactory = null;
      this.mapper = null;
      this.httpClient = client;
   }

   public ReactorClientHttpRequestFactory(ReactorResourceFactory resourceFactory, Function<HttpClient, HttpClient> mapper) {
      this.resourceFactory = resourceFactory;
      this.mapper = mapper;
      if (resourceFactory.isRunning()) {
         this.httpClient = this.createHttpClient(resourceFactory, mapper);
      }
   }

   private HttpClient createHttpClient(ReactorResourceFactory factory, Function<HttpClient, HttpClient> mapper) {
      HttpClient client = HttpClient.create(factory.getConnectionProvider());
      client = defaultInitializer.andThen(mapper).apply(client);
      client = (HttpClient)client.runOn(factory.getLoopResources());
      if (this.connectTimeout != null) {
         client = (HttpClient)client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeout);
      }

      if (this.readTimeout != null) {
         client = client.responseTimeout(this.readTimeout);
      }

      return client;
   }

   public void setExecutor(Executor executor) {
      Assert.notNull(executor, "Executor must not be null");
      this.executor = executor;
   }

   public void setConnectTimeout(int connectTimeout) {
      Assert.isTrue(connectTimeout >= 0, "Timeout must be a non-negative value");
      this.connectTimeout = connectTimeout;
      HttpClient httpClient = this.httpClient;
      if (httpClient != null) {
         this.httpClient = (HttpClient)httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeout);
      }
   }

   public void setConnectTimeout(Duration connectTimeout) {
      Assert.notNull(connectTimeout, "ConnectTimeout must not be null");
      this.setConnectTimeout((int)connectTimeout.toMillis());
   }

   public void setReadTimeout(Duration timeout) {
      Assert.notNull(timeout, "ReadTimeout must not be null");
      Assert.isTrue(timeout.toMillis() > 0L, "Timeout must be a positive value");
      this.readTimeout = timeout;
      HttpClient httpClient = this.httpClient;
      if (httpClient != null) {
         this.httpClient = httpClient.responseTimeout(timeout);
      }
   }

   public void setReadTimeout(long readTimeout) {
      this.setReadTimeout(Duration.ofMillis(readTimeout));
   }

   @Override
   public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
      HttpClient client = this.httpClient;
      if (client == null) {
         Assert.state(this.resourceFactory != null && this.mapper != null, "Expected HttpClient or ResourceFactory and mapper");
         client = this.createHttpClient(this.resourceFactory, this.mapper);
      }

      return new ReactorClientHttpRequest(client, httpMethod, uri, this.executor, this.exchangeTimeout);
   }

   public void start() {
      if (this.resourceFactory != null && this.mapper != null) {
         synchronized (this.lifecycleMonitor) {
            if (this.httpClient == null) {
               this.httpClient = this.createHttpClient(this.resourceFactory, this.mapper);
            }
         }
      } else {
         logger.warn("Restarting a ReactorClientHttpRequestFactory bean is only supported with externally managed Reactor Netty resources");
      }
   }

   public void stop() {
      if (this.resourceFactory != null && this.mapper != null) {
         synchronized (this.lifecycleMonitor) {
            this.httpClient = null;
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
