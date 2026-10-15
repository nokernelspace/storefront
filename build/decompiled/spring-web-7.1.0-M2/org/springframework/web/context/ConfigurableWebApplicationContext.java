package org.springframework.web.context;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

public interface ConfigurableWebApplicationContext extends WebApplicationContext, ConfigurableApplicationContext {
   String APPLICATION_CONTEXT_ID_PREFIX = WebApplicationContext.class.getName() + ":";
   String SERVLET_CONFIG_BEAN_NAME = "servletConfig";

   void setServletContext(@Nullable ServletContext servletContext);

   void setServletConfig(@Nullable ServletConfig servletConfig);

   @Nullable ServletConfig getServletConfig();

   void setNamespace(@Nullable String namespace);

   @Nullable String getNamespace();

   void setConfigLocation(String configLocation);

   void setConfigLocations(String... configLocations);

   String @Nullable [] getConfigLocations();
}
