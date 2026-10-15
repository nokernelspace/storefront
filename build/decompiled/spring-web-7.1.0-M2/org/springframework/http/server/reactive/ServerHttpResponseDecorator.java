package org.springframework.http.server.reactive;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public class ServerHttpResponseDecorator implements ServerHttpResponse {
   private final ServerHttpResponse delegate;

   public ServerHttpResponseDecorator(ServerHttpResponse delegate) {
      Assert.notNull(delegate, "Delegate is required");
      this.delegate = delegate;
   }

   public ServerHttpResponse getDelegate() {
      return this.delegate;
   }

   @Override
   public boolean setStatusCode(@Nullable HttpStatusCode status) {
      return this.getDelegate().setStatusCode(status);
   }

   @Override
   public @Nullable HttpStatusCode getStatusCode() {
      return this.getDelegate().getStatusCode();
   }

   @Override
   public boolean setRawStatusCode(@Nullable Integer value) {
      return this.getDelegate().setRawStatusCode(value);
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.getDelegate().getHeaders();
   }

   @Override
   public MultiValueMap<String, ResponseCookie> getCookies() {
      return this.getDelegate().getCookies();
   }

   @Override
   public void addCookie(ResponseCookie cookie) {
      this.getDelegate().addCookie(cookie);
   }

   @Override
   public DataBufferFactory bufferFactory() {
      return this.getDelegate().bufferFactory();
   }

   @Override
   public void beforeCommit(Supplier<? extends Mono<Void>> action) {
      this.getDelegate().beforeCommit(action);
   }

   @Override
   public boolean isCommitted() {
      return this.getDelegate().isCommitted();
   }

   @Override
   public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return this.getDelegate().writeWith(body);
   }

   @Override
   public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return this.getDelegate().writeAndFlushWith(body);
   }

   @Override
   public Mono<Void> setComplete() {
      return this.getDelegate().setComplete();
   }

   public static <T> T getNativeResponse(ServerHttpResponse response) {
      if (response instanceof AbstractServerHttpResponse abstractServerHttpResponse) {
         return abstractServerHttpResponse.getNativeResponse();
      } else if (response instanceof ServerHttpResponseDecorator serverHttpResponseDecorator) {
         return getNativeResponse(serverHttpResponseDecorator.getDelegate());
      } else {
         throw new IllegalArgumentException("Can't find native response in " + response.getClass().getName());
      }
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " [delegate=" + this.getDelegate() + "]";
   }
}
