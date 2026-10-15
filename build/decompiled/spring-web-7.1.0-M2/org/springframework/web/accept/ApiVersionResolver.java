package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ApiVersionResolver {
   @Nullable String resolveVersion(HttpServletRequest request);
}
