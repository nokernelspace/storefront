package org.springframework.web.cors.reactive;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class CorsWebFilter implements WebFilter {
   private final CorsConfigurationSource configSource;
   private final CorsProcessor processor;

   public CorsWebFilter(CorsConfigurationSource configSource) {
      this(configSource, new DefaultCorsProcessor());
   }

   public CorsWebFilter(CorsConfigurationSource configSource, CorsProcessor processor) {
      Assert.notNull(configSource, "CorsConfigurationSource must not be null");
      Assert.notNull(processor, "CorsProcessor must not be null");
      this.configSource = configSource;
      this.processor = processor;
   }

   @Override
   public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
      ServerHttpRequest request = exchange.getRequest();
      CorsConfiguration corsConfiguration = this.configSource.getCorsConfiguration(exchange);
      boolean isValid = this.processor.process(corsConfiguration, exchange);
      return isValid && !CorsUtils.isPreFlightRequest(request) ? chain.filter(exchange) : Mono.empty();
   }
}
