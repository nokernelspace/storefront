package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.web.WebApplicationInitializer;

public abstract class AbstractContextLoaderInitializer implements WebApplicationInitializer {
   protected final Log logger = LogFactory.getLog(this.getClass());

   @Override
   public void onStartup(ServletContext servletContext) throws ServletException {
      this.registerContextLoaderListener(servletContext);
   }

   protected void registerContextLoaderListener(ServletContext servletContext) {
      WebApplicationContext rootAppContext = this.createRootApplicationContext();
      if (rootAppContext != null) {
         ContextLoaderListener listener = new ContextLoaderListener(rootAppContext, servletContext);
         listener.setContextInitializers(this.getRootApplicationContextInitializers());
         servletContext.addListener(listener);
      } else {
         this.logger.debug("No ContextLoaderListener registered, as createRootApplicationContext() did not return an application context");
      }
   }

   protected abstract @Nullable WebApplicationContext createRootApplicationContext();

   protected ApplicationContextInitializer<?> @Nullable [] getRootApplicationContextInitializers() {
      return null;
   }
}
