package org.springframework.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@FunctionalInterface
public interface HttpRequestHandler {
   void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;
}
