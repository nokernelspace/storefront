package org.springframework.web.context.support;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.WebApplicationContext;

public class HttpRequestHandlerServlet extends HttpServlet {
   private @Nullable HttpRequestHandler target;

   public void init() throws ServletException {
      WebApplicationContext wac = WebApplicationContextUtils.getRequiredWebApplicationContext(this.getServletContext());
      this.target = (HttpRequestHandler)wac.getBean(this.getServletName(), HttpRequestHandler.class);
   }

   protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      Assert.state(this.target != null, "No HttpRequestHandler available");
      LocaleContextHolder.setLocale(request.getLocale());

      try {
         this.target.handleRequest(request, response);
      } catch (HttpRequestMethodNotSupportedException ex) {
         String[] supportedMethods = ex.getSupportedMethods();
         if (supportedMethods != null) {
            response.setHeader("Allow", StringUtils.arrayToDelimitedString(supportedMethods, ", "));
         }

         response.sendError(405, ex.getMessage());
      } finally {
         LocaleContextHolder.resetLocaleContext();
      }
   }
}
