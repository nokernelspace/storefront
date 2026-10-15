package org.springframework.web.filter.reactive;

import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

public class ServerWebExchangeContextFilter implements WebFilter {
   public static final String EXCHANGE_CONTEXT_ATTRIBUTE = ServerWebExchangeContextFilter.class.getName() + ".EXCHANGE_CONTEXT";

   @Override
   public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
      return chain.filter(exchange).contextWrite(context -> context.put(EXCHANGE_CONTEXT_ATTRIBUTE, exchange));
   }

   public static Optional<ServerWebExchange> getExchange(ContextView contextView) {
      return contextView.getOrEmpty(EXCHANGE_CONTEXT_ATTRIBUTE);
   }
}
