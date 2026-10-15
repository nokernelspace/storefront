package org.springframework.web.server.session;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

public interface WebSessionManager {
   Mono<WebSession> getSession(ServerWebExchange exchange);
}
