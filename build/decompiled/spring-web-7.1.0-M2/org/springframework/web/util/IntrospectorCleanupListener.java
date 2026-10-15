package org.springframework.web.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.beans.Introspector;
import org.springframework.beans.CachedIntrospectionResults;

public class IntrospectorCleanupListener implements ServletContextListener {
   public void contextInitialized(ServletContextEvent event) {
      CachedIntrospectionResults.acceptClassLoader(Thread.currentThread().getContextClassLoader());
   }

   public void contextDestroyed(ServletContextEvent event) {
      CachedIntrospectionResults.clearClassLoader(Thread.currentThread().getContextClassLoader());
      Introspector.flushCaches();
   }
}
