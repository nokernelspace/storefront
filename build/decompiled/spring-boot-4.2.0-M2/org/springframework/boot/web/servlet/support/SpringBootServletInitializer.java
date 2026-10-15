package org.springframework.boot.web.servlet.support;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.web.context.servlet.AnnotationConfigServletWebApplicationContext;
import org.springframework.boot.web.context.servlet.ApplicationServletEnvironment;
import org.springframework.boot.web.context.servlet.WebApplicationContextInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.scheduler.Schedulers;

public abstract class SpringBootServletInitializer implements WebApplicationInitializer {
   private static final boolean REACTOR_PRESENT = ClassUtils.isPresent("reactor.core.scheduler.Schedulers", SpringBootServletInitializer.class.getClassLoader());
   protected @Nullable Log logger;
   private boolean registerErrorPageFilter = true;

   protected final void setRegisterErrorPageFilter(boolean registerErrorPageFilter) {
      this.registerErrorPageFilter = registerErrorPageFilter;
   }

   public void onStartup(ServletContext servletContext) throws ServletException {
      servletContext.setAttribute("logging.register-shutdown-hook", false);
      this.logger = LogFactory.getLog(this.getClass());
      WebApplicationContext rootApplicationContext = this.createRootApplicationContext(servletContext);
      if (rootApplicationContext != null) {
         servletContext.addListener(new SpringBootServletInitializer.SpringBootContextLoaderListener(rootApplicationContext, servletContext));
      } else {
         this.logger.debug("No ContextLoaderListener registered, as createRootApplicationContext() did not return an application context");
      }
   }

   protected void deregisterJdbcDrivers(ServletContext servletContext) {
      for (Driver driver : Collections.list(DriverManager.getDrivers())) {
         if (driver.getClass().getClassLoader() == servletContext.getClassLoader()) {
            try {
               DriverManager.deregisterDriver(driver);
            } catch (SQLException var5) {
            }
         }
      }
   }

   protected void shutDownSharedReactorSchedulers(ServletContext servletContext) {
      if (Schedulers.class.getClassLoader() == servletContext.getClassLoader()) {
         Schedulers.shutdownNow();
      }
   }

   protected @Nullable WebApplicationContext createRootApplicationContext(ServletContext servletContext) {
      SpringApplicationBuilder builder = this.createSpringApplicationBuilder();
      builder.main(this.getClass());
      ApplicationContext parent = this.getExistingRootWebApplicationContext(servletContext);
      if (parent != null) {
         this.getLogger().info("Root context already created (using as parent).");
         servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, null);
         builder.initializers(new ParentContextApplicationContextInitializer(parent));
      }

      builder.initializers(new ServletContextApplicationContextInitializer(servletContext));
      builder.contextFactory(new SpringBootServletInitializer.WarDeploymentApplicationContextFactory(servletContext));
      builder = this.configure(builder);
      builder.listeners(new SpringBootServletInitializer.WebEnvironmentPropertySourceInitializer(servletContext));
      SpringApplication application = builder.build();
      if (application.getAllSources().isEmpty() && MergedAnnotations.from(this.getClass(), SearchStrategy.TYPE_HIERARCHY).isPresent(Configuration.class)) {
         application.addPrimarySources(Collections.singleton(this.getClass()));
      }

      Assert.state(
         !application.getAllSources().isEmpty(),
         "No SpringApplication sources have been defined. Either override the configure method or add an @Configuration annotation"
      );
      if (this.registerErrorPageFilter) {
         application.addPrimarySources(Collections.singleton(ErrorPageFilterConfiguration.class));
      }

      application.setRegisterShutdownHook(false);
      return this.run(application);
   }

   private Log getLogger() {
      Assert.state(this.logger != null, "Logger not set");
      return this.logger;
   }

   protected SpringApplicationBuilder createSpringApplicationBuilder() {
      return new SpringApplicationBuilder();
   }

   protected @Nullable WebApplicationContext run(SpringApplication application) {
      return (WebApplicationContext)application.run();
   }

   private @Nullable ApplicationContext getExistingRootWebApplicationContext(ServletContext servletContext) {
      return servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) instanceof ApplicationContext applicationContext
         ? applicationContext
         : null;
   }

   protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
      return builder;
   }

   private class SpringBootContextLoaderListener extends ContextLoaderListener {
      private final ServletContext servletContext;

      SpringBootContextLoaderListener(WebApplicationContext applicationContext, ServletContext servletContext) {
         super(applicationContext);
         this.servletContext = servletContext;
      }

      public void contextInitialized(ServletContextEvent event) {
      }

      public void contextDestroyed(ServletContextEvent event) {
         try {
            super.contextDestroyed(event);
         } finally {
            SpringBootServletInitializer.this.deregisterJdbcDrivers(this.servletContext);
            if (SpringBootServletInitializer.REACTOR_PRESENT) {
               SpringBootServletInitializer.this.shutDownSharedReactorSchedulers(this.servletContext);
            }
         }
      }
   }

   private static final class WarDeploymentApplicationContextFactory implements ApplicationContextFactory {
      private final ServletContext servletContext;

      private WarDeploymentApplicationContextFactory(ServletContext servletContext) {
         this.servletContext = servletContext;
      }

      @Override
      public @Nullable Class<? extends ConfigurableEnvironment> getEnvironmentType(@Nullable WebApplicationType webApplicationType) {
         return webApplicationType != WebApplicationType.SERVLET ? null : ApplicationServletEnvironment.class;
      }

      @Override
      public ConfigurableEnvironment createEnvironment(@Nullable WebApplicationType webApplicationType) {
         return new ApplicationServletEnvironment();
      }

      @Override
      public ConfigurableApplicationContext create(@Nullable WebApplicationType webApplicationType) {
         return new AnnotationConfigServletWebApplicationContext() {
            protected void onRefresh() {
               super.onRefresh();

               try {
                  new WebApplicationContextInitializer(this).initialize(WarDeploymentApplicationContextFactory.this.servletContext);
               } catch (ServletException ex) {
                  throw new ApplicationContextException("Cannot initialize servlet context", ex);
               }
            }
         };
      }
   }

   private static final class WebEnvironmentPropertySourceInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
      private final ServletContext servletContext;

      private WebEnvironmentPropertySourceInitializer(ServletContext servletContext) {
         this.servletContext = servletContext;
      }

      public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
         if (event.getEnvironment() instanceof ConfigurableWebEnvironment configurableWebEnvironment) {
            configurableWebEnvironment.initPropertySources(this.servletContext, null);
         }
      }

      public int getOrder() {
         return Integer.MIN_VALUE;
      }
   }
}
