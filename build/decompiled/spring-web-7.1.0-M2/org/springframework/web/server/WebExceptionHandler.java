package org.springframework.web.server;

import reactor.core.publisher.Mono;

public interface WebExceptionHandler {
   Mono<Void> handle(ServerWebExchange exchange, Throwable ex);
}
