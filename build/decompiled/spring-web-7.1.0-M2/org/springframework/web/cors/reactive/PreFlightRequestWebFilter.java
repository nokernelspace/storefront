package org.springframework.web.cors.reactive;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class PreFlightRequestWebFilter implements WebFilter {
   private final PreFlightRequestHandler handler;

   public PreFlightRequestWebFilter(PreFlightRequestHandler handler) {
      Assert.notNull(handler, "PreFlightRequestHandler is required");
      this.handler = handler;
   }

   @Override
   public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
      return CorsUtils.isPreFlightRequest(exchange.getRequest()) ? this.handler.handlePreFlight(exchange) : chain.filter(exchange);
   }
}
