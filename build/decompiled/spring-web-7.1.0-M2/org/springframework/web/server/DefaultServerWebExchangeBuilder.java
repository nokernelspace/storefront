package org.springframework.web.server;

import java.security.Principal;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

class DefaultServerWebExchangeBuilder implements ServerWebExchange.Builder {
   private final ServerWebExchange delegate;
   private @Nullable ServerHttpRequest request;
   private @Nullable ServerHttpResponse response;
   private @Nullable Mono<Principal> principalMono;

   DefaultServerWebExchangeBuilder(ServerWebExchange delegate) {
      Assert.notNull(delegate, "Delegate is required");
      this.delegate = delegate;
   }

   @Override
   public ServerWebExchange.Builder request(Consumer<ServerHttpRequest.Builder> consumer) {
      ServerHttpRequest.Builder builder = this.delegate.getRequest().mutate();
      consumer.accept(builder);
      return this.request(builder.build());
   }

   @Override
   public ServerWebExchange.Builder request(ServerHttpRequest request) {
      this.request = request;
      return this;
   }

   @Override
   public ServerWebExchange.Builder response(ServerHttpResponse response) {
      this.response = response;
      return this;
   }

   @Override
   public ServerWebExchange.Builder principal(Mono<Principal> principalMono) {
      this.principalMono = principalMono;
      return this;
   }

   @Override
   public ServerWebExchange build() {
      return new DefaultServerWebExchangeBuilder.MutativeDecorator(this.delegate, this.request, this.response, this.principalMono);
   }

   private static class MutativeDecorator extends ServerWebExchangeDecorator {
      private final @Nullable ServerHttpRequest request;
      private final @Nullable ServerHttpResponse response;
      private final @Nullable Mono<Principal> principalMono;

      public MutativeDecorator(
         ServerWebExchange delegate, @Nullable ServerHttpRequest request, @Nullable ServerHttpResponse response, @Nullable Mono<Principal> principalMono
      ) {
         super(delegate);
         this.request = request;
         this.response = response;
         this.principalMono = principalMono;
      }

      @Override
      public ServerHttpRequest getRequest() {
         return this.request != null ? this.request : this.getDelegate().getRequest();
      }

      @Override
      public ServerHttpResponse getResponse() {
         return this.response != null ? this.response : this.getDelegate().getResponse();
      }

      @Override
      public <T extends Principal> Mono<T> getPrincipal() {
         return (Mono<T>)(this.principalMono != null ? this.principalMono : this.getDelegate().getPrincipal());
      }
   }
}
