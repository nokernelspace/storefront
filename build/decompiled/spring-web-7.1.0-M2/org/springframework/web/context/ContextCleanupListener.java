package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Enumeration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;

public class ContextCleanupListener implements ServletContextListener {
   private static final Log logger = LogFactory.getLog(ContextCleanupListener.class);

   public void contextInitialized(ServletContextEvent event) {
   }

   public void contextDestroyed(ServletContextEvent event) {
      cleanupAttributes(event.getServletContext());
   }

   static void cleanupAttributes(ServletContext servletContext) {
      Enumeration<String> attrNames = servletContext.getAttributeNames();

      while (attrNames.hasMoreElements()) {
         String attrName = attrNames.nextElement();
         if (attrName.startsWith("org.springframework.") && servletContext.getAttribute(attrName) instanceof DisposableBean disposableBean) {
            try {
               disposableBean.destroy();
            } catch (Throwable ex) {
               if (logger.isWarnEnabled()) {
                  logger.warn("Invocation of destroy method failed on ServletContext attribute with name '" + attrName + "'", ex);
               }
            }
         }
      }
   }
}
