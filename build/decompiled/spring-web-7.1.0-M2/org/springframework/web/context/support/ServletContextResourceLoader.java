package org.springframework.web.context.support;

import jakarta.servlet.ServletContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

public class ServletContextResourceLoader extends DefaultResourceLoader {
   private final ServletContext servletContext;

   public ServletContextResourceLoader(ServletContext servletContext) {
      this.servletContext = servletContext;
   }

   protected Resource getResourceByPath(String path) {
      return new ServletContextResource(this.servletContext, path);
   }
}
