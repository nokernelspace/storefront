package org.springframework.web.cors.reactive;

import org.jspecify.annotations.Nullable;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;

public interface CorsConfigurationSource {
   @Nullable CorsConfiguration getCorsConfiguration(ServerWebExchange exchange);
}
