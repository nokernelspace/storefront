package org.springframework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.PreFlightRequestHandler;

public class PreFlightRequestFilter extends OncePerRequestFilter {
   private final PreFlightRequestHandler handler;

   public PreFlightRequestFilter(PreFlightRequestHandler handler) {
      Assert.notNull(handler, "PreFlightRequestHandler is required");
      this.handler = handler;
   }

   @Override
   protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
      if (!CorsUtils.isPreFlightRequest(request)) {
         chain.doFilter(request, response);
      } else {
         try {
            this.handler.handlePreFlight(request, response);
         } catch (ServletException | IOException ex) {
            throw ex;
         } catch (Throwable ex) {
            throw new ServletException("Pre-flight request handling failed: " + ex, ex);
         }
      }
   }
}
