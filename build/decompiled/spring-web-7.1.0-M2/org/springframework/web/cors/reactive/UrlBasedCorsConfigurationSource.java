package org.springframework.web.cors.reactive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class UrlBasedCorsConfigurationSource implements CorsConfigurationSource {
   private final PathPatternParser patternParser;
   private final Map<PathPattern, CorsConfiguration> corsConfigurations = new LinkedHashMap<>();

   public UrlBasedCorsConfigurationSource() {
      this(PathPatternParser.defaultInstance);
   }

   public UrlBasedCorsConfigurationSource(PathPatternParser patternParser) {
      this.patternParser = patternParser;
   }

   public void setCorsConfigurations(@Nullable Map<String, CorsConfiguration> configMap) {
      this.corsConfigurations.clear();
      if (configMap != null) {
         configMap.forEach(this::registerCorsConfiguration);
      }
   }

   public void registerCorsConfiguration(String path, CorsConfiguration config) {
      this.corsConfigurations.put(this.patternParser.parse(path), config);
   }

   @Override
   public @Nullable CorsConfiguration getCorsConfiguration(ServerWebExchange exchange) {
      PathContainer path = exchange.getRequest().getPath().pathWithinApplication();

      for (Entry<PathPattern, CorsConfiguration> entry : this.corsConfigurations.entrySet()) {
         if (entry.getKey().matches(path)) {
            return entry.getValue();
         }
      }

      return null;
   }
}
