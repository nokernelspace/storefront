package org.springframework.http.client.reactive;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

public class ClientHttpResponseDecorator implements ClientHttpResponse {
   private final ClientHttpResponse delegate;

   public ClientHttpResponseDecorator(ClientHttpResponse delegate) {
      Assert.notNull(delegate, "Delegate is required");
      this.delegate = delegate;
   }

   public ClientHttpResponse getDelegate() {
      return this.delegate;
   }

   @Override
   public String getId() {
      return this.delegate.getId();
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return this.delegate.getStatusCode();
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.delegate.getHeaders();
   }

   @Override
   public MultiValueMap<String, ResponseCookie> getCookies() {
      return this.delegate.getCookies();
   }

   @Override
   public Flux<DataBuffer> getBody() {
      return this.delegate.getBody();
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " [delegate=" + this.getDelegate() + "]";
   }
}
