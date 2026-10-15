package org.springframework.web.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class WebAppRootListener implements ServletContextListener {
   public void contextInitialized(ServletContextEvent event) {
      WebUtils.setWebAppRootSystemProperty(event.getServletContext());
   }

   public void contextDestroyed(ServletContextEvent event) {
      WebUtils.removeWebAppRootSystemProperty(event.getServletContext());
   }
}
