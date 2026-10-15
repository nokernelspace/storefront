package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

public interface CorsProcessor {
   boolean processRequest(@Nullable CorsConfiguration configuration, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
