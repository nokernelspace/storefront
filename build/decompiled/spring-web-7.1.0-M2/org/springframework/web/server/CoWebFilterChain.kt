package org.springframework.web.server

public interface CoWebFilterChain {
   public abstract suspend fun filter(exchange: ServerWebExchange) {
   }
}
