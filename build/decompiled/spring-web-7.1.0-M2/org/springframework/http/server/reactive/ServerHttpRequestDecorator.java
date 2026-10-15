package org.springframework.http.server.reactive;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

public class ServerHttpRequestDecorator implements ServerHttpRequest {
   private final ServerHttpRequest delegate;

   public ServerHttpRequestDecorator(ServerHttpRequest delegate) {
      Assert.notNull(delegate, "Delegate is required");
      this.delegate = delegate;
   }

   public ServerHttpRequest getDelegate() {
      return this.delegate;
   }

   @Override
   public String getId() {
      return this.getDelegate().getId();
   }

   @Override
   public HttpMethod getMethod() {
      return this.getDelegate().getMethod();
   }

   @Override
   public URI getURI() {
      return this.getDelegate().getURI();
   }

   @Override
   public Map<String, Object> getAttributes() {
      return this.getDelegate().getAttributes();
   }

   @Override
   public RequestPath getPath() {
      return this.getDelegate().getPath();
   }

   @Override
   public MultiValueMap<String, String> getQueryParams() {
      return this.getDelegate().getQueryParams();
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.getDelegate().getHeaders();
   }

   @Override
   public MultiValueMap<String, HttpCookie> getCookies() {
      return this.getDelegate().getCookies();
   }

   @Override
   public @Nullable InetSocketAddress getLocalAddress() {
      return this.getDelegate().getLocalAddress();
   }

   @Override
   public @Nullable InetSocketAddress getRemoteAddress() {
      return this.getDelegate().getRemoteAddress();
   }

   @Override
   public @Nullable SslInfo getSslInfo() {
      return this.getDelegate().getSslInfo();
   }

   @Override
   public Flux<DataBuffer> getBody() {
      return this.getDelegate().getBody();
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " [delegate=" + this.getDelegate() + "]";
   }

   public static <T> T getNativeRequest(ServerHttpRequest request) {
      if (request instanceof AbstractServerHttpRequest abstractServerHttpRequest) {
         return abstractServerHttpRequest.getNativeRequest();
      } else if (request instanceof ServerHttpRequestDecorator serverHttpRequestDecorator) {
         return getNativeRequest(serverHttpRequestDecorator.getDelegate());
      } else {
         throw new IllegalArgumentException("Cannot find native request in " + request.getClass().getName());
      }
   }
}
