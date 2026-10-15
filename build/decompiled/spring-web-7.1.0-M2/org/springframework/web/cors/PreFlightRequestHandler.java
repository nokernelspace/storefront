package org.springframework.web.cors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface PreFlightRequestHandler {
   void handlePreFlight(HttpServletRequest request, HttpServletResponse response) throws Exception;
}
