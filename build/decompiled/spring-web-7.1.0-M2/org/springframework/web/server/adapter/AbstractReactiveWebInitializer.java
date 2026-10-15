package org.springframework.web.server.adapter;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration.Dynamic;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.WebApplicationInitializer;

public abstract class AbstractReactiveWebInitializer implements WebApplicationInitializer {
   public static final String DEFAULT_SERVLET_NAME = "http-handler-adapter";

   @Override
   public void onStartup(ServletContext servletContext) throws ServletException {
      String servletName = this.getServletName();
      Assert.state(StringUtils.hasLength(servletName), "getServletName() must not return null or empty");
      ApplicationContext applicationContext = this.createApplicationContext();
      Assert.state(applicationContext != null, "createApplicationContext() must not return null");
      this.refreshApplicationContext(applicationContext);
      this.registerCloseListener(servletContext, applicationContext);
      HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(applicationContext).build();
      ServletHttpHandlerAdapter servlet = new ServletHttpHandlerAdapter(httpHandler);
      Dynamic registration = servletContext.addServlet(servletName, servlet);
      if (registration == null) {
         throw new IllegalStateException(
            "Failed to register servlet with name '" + servletName + "'. Check if there is another servlet registered under the same name."
         );
      }

      registration.setLoadOnStartup(1);
      registration.addMapping(new String[]{this.getServletMapping()});
      registration.setAsyncSupported(true);
   }

   protected String getServletName() {
      return "http-handler-adapter";
   }

   protected ApplicationContext createApplicationContext() {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      Class<?>[] configClasses = this.getConfigClasses();
      Assert.state(!ObjectUtils.isEmpty(configClasses), "No Spring configuration provided through getConfigClasses()");
      context.register(configClasses);
      return context;
   }

   protected abstract Class<?>[] getConfigClasses();

   protected void refreshApplicationContext(ApplicationContext context) {
      if (context instanceof ConfigurableApplicationContext cac && !cac.isActive()) {
         cac.refresh();
      }
   }

   protected void registerCloseListener(ServletContext servletContext, ApplicationContext applicationContext) {
      if (applicationContext instanceof ConfigurableApplicationContext cac) {
         servletContext.addListener(new AbstractReactiveWebInitializer.ServletContextDestroyedListener(cac));
      }
   }

   protected String getServletMapping() {
      return "/";
   }

   private static class ServletContextDestroyedListener implements ServletContextListener {
      private final ConfigurableApplicationContext applicationContext;

      public ServletContextDestroyedListener(ConfigurableApplicationContext applicationContext) {
         this.applicationContext = applicationContext;
      }

      public void contextInitialized(ServletContextEvent sce) {
      }

      public void contextDestroyed(ServletContextEvent sce) {
         this.applicationContext.close();
      }
   }
}
