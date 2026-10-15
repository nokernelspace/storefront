package org.springframework.http.client;

import java.io.IOException;
import java.net.URI;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

public class BufferingClientHttpRequestFactory extends AbstractClientHttpRequestFactoryWrapper {
   private final BiPredicate<URI, HttpMethod> bufferingPredicate;

   public BufferingClientHttpRequestFactory(ClientHttpRequestFactory requestFactory) {
      this(requestFactory, null);
   }

   public BufferingClientHttpRequestFactory(ClientHttpRequestFactory requestFactory, @Nullable BiPredicate<URI, HttpMethod> bufferingPredicate) {
      super(requestFactory);
      this.bufferingPredicate = bufferingPredicate != null ? bufferingPredicate : (uri, method) -> true;
   }

   @Override
   protected ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) throws IOException {
      ClientHttpRequest request = requestFactory.createRequest(uri, httpMethod);
      return this.shouldBuffer(uri, httpMethod) ? new BufferingClientHttpRequestWrapper(request) : request;
   }

   protected boolean shouldBuffer(URI uri, HttpMethod httpMethod) {
      return this.bufferingPredicate.test(uri, httpMethod);
   }
}
