package org.springframework.web.server.session;

import java.util.List;
import org.springframework.web.server.ServerWebExchange;

public interface WebSessionIdResolver {
   List<String> resolveSessionIds(ServerWebExchange exchange);

   void setSessionId(ServerWebExchange exchange, String sessionId);

   void expireSession(ServerWebExchange exchange);
}
