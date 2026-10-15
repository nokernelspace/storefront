package org.springframework.web.context;

import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;

public interface WebApplicationContext extends ApplicationContext {
   String ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE = WebApplicationContext.class.getName() + ".ROOT";
   String SCOPE_REQUEST = "request";
   String SCOPE_SESSION = "session";
   String SCOPE_APPLICATION = "application";
   String SERVLET_CONTEXT_BEAN_NAME = "servletContext";
   String CONTEXT_PARAMETERS_BEAN_NAME = "contextParameters";
   String CONTEXT_ATTRIBUTES_BEAN_NAME = "contextAttributes";

   @Nullable ServletContext getServletContext();
}
