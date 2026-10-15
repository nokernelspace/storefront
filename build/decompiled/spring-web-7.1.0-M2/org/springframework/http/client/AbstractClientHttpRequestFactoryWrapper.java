package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

public abstract class AbstractClientHttpRequestFactoryWrapper implements ClientHttpRequestFactory {
   private final ClientHttpRequestFactory requestFactory;

   protected AbstractClientHttpRequestFactoryWrapper(ClientHttpRequestFactory requestFactory) {
      Assert.notNull(requestFactory, "ClientHttpRequestFactory must not be null");
      this.requestFactory = requestFactory;
   }

   @Override
   public final ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
      return this.createRequest(uri, httpMethod, this.requestFactory);
   }

   public ClientHttpRequestFactory getDelegate() {
      return this.requestFactory;
   }

   protected abstract ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) throws IOException;
}
