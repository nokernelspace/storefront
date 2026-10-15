package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

public interface CorsConfigurationSource {
   @Nullable CorsConfiguration getCorsConfiguration(HttpServletRequest request);
}
