package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.Aware;

public interface ServletContextAware extends Aware {
   void setServletContext(ServletContext servletContext);
}
