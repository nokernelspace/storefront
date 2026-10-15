package org.springframework.web.cors.reactive;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface PreFlightRequestHandler {
   Mono<Void> handlePreFlight(ServerWebExchange exchange);
}
