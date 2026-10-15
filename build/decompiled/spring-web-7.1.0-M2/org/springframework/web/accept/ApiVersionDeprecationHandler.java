package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ApiVersionDeprecationHandler {
   void handleVersion(Comparable<?> version, Object handler, HttpServletRequest request, HttpServletResponse response);
}
