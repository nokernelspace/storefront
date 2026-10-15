package org.springframework.web.server;

import reactor.core.publisher.Mono;

public interface WebHandler {
   Mono<Void> handle(ServerWebExchange exchange);
}
