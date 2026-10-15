package org.springframework.http.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.RequestConfig.Builder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

public class HttpComponentsClientHttpRequestFactory implements ClientHttpRequestFactory, DisposableBean {
   private HttpClient httpClient;
   private @Nullable BiFunction<HttpMethod, URI, HttpContext> httpContextFactory;
   private long connectionRequestTimeout = -1L;
   private long readTimeout = -1L;

   public HttpComponentsClientHttpRequestFactory() {
      this.httpClient = HttpClients.createSystem();
   }

   public HttpComponentsClientHttpRequestFactory(HttpClient httpClient) {
      this.httpClient = httpClient;
   }

   public void setHttpClient(HttpClient httpClient) {
      Assert.notNull(httpClient, "HttpClient must not be null");
      this.httpClient = httpClient;
   }

   public HttpClient getHttpClient() {
      return this.httpClient;
   }

   public void setConnectionRequestTimeout(int connectionRequestTimeout) {
      Assert.isTrue(connectionRequestTimeout >= 0, "Timeout must be a non-negative value");
      this.connectionRequestTimeout = connectionRequestTimeout;
   }

   public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
      Assert.notNull(connectionRequestTimeout, "ConnectionRequestTimeout must not be null");
      Assert.isTrue(!connectionRequestTimeout.isNegative(), "Timeout must be a non-negative value");
      this.connectionRequestTimeout = connectionRequestTimeout.toMillis();
   }

   public void setReadTimeout(int readTimeout) {
      Assert.isTrue(readTimeout >= 0, "Timeout must be a non-negative value");
      this.readTimeout = readTimeout;
   }

   public void setReadTimeout(Duration readTimeout) {
      Assert.notNull(readTimeout, "ReadTimeout must not be null");
      Assert.isTrue(!readTimeout.isNegative(), "Timeout must be a non-negative value");
      this.readTimeout = readTimeout.toMillis();
   }

   public void setHttpContextFactory(BiFunction<HttpMethod, URI, HttpContext> httpContextFactory) {
      this.httpContextFactory = httpContextFactory;
   }

   @Override
   public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
      HttpClient client = this.getHttpClient();
      ClassicHttpRequest httpRequest = this.createHttpUriRequest(httpMethod, uri);
      this.postProcessHttpRequest(httpRequest);
      HttpContext context = this.createHttpContext(httpMethod, uri);
      if (context == null) {
         context = HttpClientContext.create();
      }

      if (!hasCustomRequestConfig(context)) {
         RequestConfig config = null;
         if (httpRequest instanceof Configurable configurable) {
            config = configurable.getConfig();
         }

         if (config == null) {
            config = this.createRequestConfig(client);
         }

         if (config != null) {
            if (context instanceof HttpClientContext clientContext) {
               clientContext.setRequestConfig(config);
            }

            context.setAttribute("http.request-config", config);
         }
      }

      return new HttpComponentsClientHttpRequest(client, httpRequest, context);
   }

   private static boolean hasCustomRequestConfig(HttpContext context) {
      if (!(context instanceof HttpClientContext clientContext)) {
         return context.getAttribute("http.request-config") != null;
      } else {
         RequestConfig requestConfig = clientContext.getRequestConfig();
         return requestConfig != null && !requestConfig.equals(RequestConfig.DEFAULT);
      }
   }

   protected @Nullable RequestConfig createRequestConfig(Object client) {
      if (client instanceof Configurable configurableClient) {
         RequestConfig clientRequestConfig = configurableClient.getConfig();
         return this.mergeRequestConfig(clientRequestConfig);
      } else {
         return this.mergeRequestConfig(RequestConfig.DEFAULT);
      }
   }

   protected RequestConfig mergeRequestConfig(RequestConfig clientConfig) {
      if (this.connectionRequestTimeout == -1L && this.readTimeout == -1L) {
         return clientConfig;
      }

      Builder builder = RequestConfig.copy(clientConfig);
      if (this.connectionRequestTimeout >= 0L) {
         builder.setConnectionRequestTimeout(this.connectionRequestTimeout, TimeUnit.MILLISECONDS);
      }

      if (this.readTimeout >= 0L) {
         builder.setResponseTimeout(this.readTimeout, TimeUnit.MILLISECONDS);
      }

      return builder.build();
   }

   protected ClassicHttpRequest createHttpUriRequest(HttpMethod httpMethod, URI uri) {
      if (HttpMethod.GET.equals(httpMethod)) {
         return new HttpGet(uri);
      } else if (HttpMethod.HEAD.equals(httpMethod)) {
         return new HttpHead(uri);
      } else if (HttpMethod.POST.equals(httpMethod)) {
         return new HttpPost(uri);
      } else if (HttpMethod.PUT.equals(httpMethod)) {
         return new HttpPut(uri);
      } else if (HttpMethod.PATCH.equals(httpMethod)) {
         return new HttpPatch(uri);
      } else if (HttpMethod.DELETE.equals(httpMethod)) {
         return new HttpDelete(uri);
      } else if (HttpMethod.OPTIONS.equals(httpMethod)) {
         return new HttpOptions(uri);
      } else if (HttpMethod.TRACE.equals(httpMethod)) {
         return new HttpTrace(uri);
      } else if (HttpMethod.QUERY.equals(httpMethod)) {
         return new HttpUriRequestBase(HttpMethod.QUERY.name(), uri);
      } else {
         throw new IllegalArgumentException("Invalid HTTP method: " + httpMethod);
      }
   }

   protected void postProcessHttpRequest(ClassicHttpRequest request) {
   }

   protected @Nullable HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
      return this.httpContextFactory != null ? this.httpContextFactory.apply(httpMethod, uri) : null;
   }

   public void destroy() throws Exception {
      if (this.getHttpClient() instanceof Closeable closeable) {
         closeable.close();
      }
   }
}
