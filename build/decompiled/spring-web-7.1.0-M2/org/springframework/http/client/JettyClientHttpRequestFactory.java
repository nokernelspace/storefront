package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

public class JettyClientHttpRequestFactory implements ClientHttpRequestFactory, InitializingBean, DisposableBean {
   private final HttpClient httpClient;
   private final boolean defaultClient;
   private long readTimeout = 10000L;

   public JettyClientHttpRequestFactory() {
      this(new HttpClient(), true);
   }

   public JettyClientHttpRequestFactory(HttpClient httpClient) {
      this(httpClient, false);
   }

   private JettyClientHttpRequestFactory(HttpClient httpClient, boolean defaultClient) {
      this.httpClient = httpClient;
      this.defaultClient = defaultClient;
   }

   public void setConnectTimeout(int connectTimeout) {
      Assert.isTrue(connectTimeout >= 0, "Timeout must be a non-negative value");
      this.httpClient.setConnectTimeout(connectTimeout);
   }

   public void setConnectTimeout(Duration connectTimeout) {
      Assert.notNull(connectTimeout, "ConnectTimeout must not be null");
      this.httpClient.setConnectTimeout(connectTimeout.toMillis());
   }

   public void setReadTimeout(long readTimeout) {
      Assert.isTrue(readTimeout > 0L, "Timeout must be a positive value");
      this.readTimeout = readTimeout;
   }

   public void setReadTimeout(Duration readTimeout) {
      Assert.notNull(readTimeout, "ReadTimeout must not be null");
      this.readTimeout = readTimeout.toMillis();
   }

   public void afterPropertiesSet() throws Exception {
      this.startHttpClient();
   }

   private void startHttpClient() throws Exception {
      if (!this.httpClient.isStarted()) {
         this.httpClient.start();
      }
   }

   public void destroy() throws Exception {
      if (this.defaultClient && !this.httpClient.isStopped()) {
         this.httpClient.stop();
      }
   }

   @Override
   public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
      try {
         this.startHttpClient();
      } catch (Exception ex) {
         throw new IOException("Could not start HttpClient: " + ex.getMessage(), ex);
      }

      Request request = this.httpClient.newRequest(uri).method(httpMethod.name());
      request.timeout(this.readTimeout, TimeUnit.MILLISECONDS);
      return new JettyClientHttpRequest(request, this.readTimeout);
   }
}
