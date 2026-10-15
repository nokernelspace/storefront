package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

public class JdkClientHttpRequestFactory implements ClientHttpRequestFactory {
   private final HttpClient httpClient;
   private final Executor executor;
   private @Nullable Duration readTimeout;
   private boolean compression = true;

   public JdkClientHttpRequestFactory() {
      this(HttpClient.newHttpClient());
   }

   public JdkClientHttpRequestFactory(HttpClient httpClient) {
      Assert.notNull(httpClient, "HttpClient is required");
      this.httpClient = httpClient;
      this.executor = httpClient.executor().orElseGet(SimpleAsyncTaskExecutor::new);
   }

   public JdkClientHttpRequestFactory(HttpClient httpClient, Executor executor) {
      Assert.notNull(httpClient, "HttpClient is required");
      Assert.notNull(executor, "Executor must not be null");
      this.httpClient = httpClient;
      this.executor = executor;
   }

   public void setReadTimeout(int readTimeout) {
      this.readTimeout = readTimeout == 0 ? null : Duration.ofMillis(readTimeout);
   }

   public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = Duration.ZERO.equals(readTimeout) ? null : readTimeout;
   }

   public void enableCompression(boolean enable) {
      this.compression = enable;
   }

   @Override
   public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
      return new JdkClientHttpRequest(this.httpClient, uri, httpMethod, this.executor, this.readTimeout, this.compression);
   }
}
