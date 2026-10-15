package org.springframework.web.server.handler;

import java.util.List;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

public class FilteringWebHandler extends WebHandlerDecorator {
   private final DefaultWebFilterChain chain;

   public FilteringWebHandler(WebHandler handler, List<WebFilter> filters) {
      super(handler);
      this.chain = new DefaultWebFilterChain(handler, filters);
   }

   public List<WebFilter> getFilters() {
      return this.chain.getFilters();
   }

   @Override
   public Mono<Void> handle(ServerWebExchange exchange) {
      return this.chain.filter(exchange);
   }
}
