package org.springframework.http.client;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

public class InterceptingClientHttpRequestFactory extends AbstractClientHttpRequestFactoryWrapper {
   private final List<ClientHttpRequestInterceptor> interceptors;
   private final BiPredicate<URI, HttpMethod> bufferingPredicate;

   public InterceptingClientHttpRequestFactory(ClientHttpRequestFactory requestFactory, @Nullable List<ClientHttpRequestInterceptor> interceptors) {
      this(requestFactory, interceptors, null);
   }

   public InterceptingClientHttpRequestFactory(
      ClientHttpRequestFactory requestFactory,
      @Nullable List<ClientHttpRequestInterceptor> interceptors,
      @Nullable BiPredicate<URI, HttpMethod> bufferingPredicate
   ) {
      super(requestFactory);
      this.interceptors = interceptors != null ? interceptors : Collections.emptyList();
      this.bufferingPredicate = bufferingPredicate != null ? bufferingPredicate : (uri, method) -> false;
   }

   @Override
   protected ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod, ClientHttpRequestFactory requestFactory) {
      return new InterceptingClientHttpRequest(requestFactory, this.interceptors, uri, httpMethod, this.bufferingPredicate);
   }
}
