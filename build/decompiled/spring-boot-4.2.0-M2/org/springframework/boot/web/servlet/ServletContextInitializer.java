package org.springframework.boot.web.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

@FunctionalInterface
public interface ServletContextInitializer {
   void onStartup(ServletContext servletContext) throws ServletException;
}
