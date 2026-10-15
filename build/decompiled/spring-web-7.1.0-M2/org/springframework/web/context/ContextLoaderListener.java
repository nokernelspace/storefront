package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.jspecify.annotations.Nullable;

public class ContextLoaderListener extends ContextLoader implements ServletContextListener {
   private @Nullable ServletContext servletContext;

   public ContextLoaderListener() {
   }

   public ContextLoaderListener(WebApplicationContext rootContext) {
      super(rootContext);
   }

   public ContextLoaderListener(WebApplicationContext rootContext, ServletContext servletContext) {
      super(rootContext);
      this.servletContext = servletContext;
   }

   public void contextInitialized(ServletContextEvent event) {
      ServletContext scToUse = this.getServletContextToUse(event);
      this.initWebApplicationContext(scToUse);
   }

   public void contextDestroyed(ServletContextEvent event) {
      ServletContext scToUse = this.getServletContextToUse(event);
      this.closeWebApplicationContext(scToUse);
      ContextCleanupListener.cleanupAttributes(scToUse);
   }

   private ServletContext getServletContextToUse(ServletContextEvent event) {
      return this.servletContext != null ? this.servletContext : event.getServletContext();
   }
}
