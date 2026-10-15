package org.springframework.web.server.handler;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

public class WebHandlerDecorator implements WebHandler {
   private final WebHandler delegate;

   public WebHandlerDecorator(WebHandler delegate) {
      Assert.notNull(delegate, "'delegate' must not be null");
      this.delegate = delegate;
   }

   public WebHandler getDelegate() {
      return this.delegate;
   }

   @Override
   public Mono<Void> handle(ServerWebExchange exchange) {
      return this.delegate.handle(exchange);
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + " [delegate=" + this.delegate + "]";
   }
}
