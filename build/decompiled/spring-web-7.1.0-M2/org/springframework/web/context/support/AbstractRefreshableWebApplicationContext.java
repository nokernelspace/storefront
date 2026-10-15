package org.springframework.web.context.support;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.AbstractRefreshableConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

public abstract class AbstractRefreshableWebApplicationContext extends AbstractRefreshableConfigApplicationContext implements ConfigurableWebApplicationContext {
   private @Nullable ServletContext servletContext;
   private @Nullable ServletConfig servletConfig;
   private @Nullable String namespace;

   public AbstractRefreshableWebApplicationContext() {
      this.setDisplayName("Root WebApplicationContext");
   }

   @Override
   public void setServletContext(@Nullable ServletContext servletContext) {
      this.servletContext = servletContext;
   }

   @Override
   public @Nullable ServletContext getServletContext() {
      return this.servletContext;
   }

   @Override
   public void setServletConfig(@Nullable ServletConfig servletConfig) {
      this.servletConfig = servletConfig;
      if (servletConfig != null && this.servletContext == null) {
         this.setServletContext(servletConfig.getServletContext());
      }
   }

   @Override
   public @Nullable ServletConfig getServletConfig() {
      return this.servletConfig;
   }

   @Override
   public void setNamespace(@Nullable String namespace) {
      this.namespace = namespace;
      if (namespace != null) {
         this.setDisplayName("WebApplicationContext for namespace '" + namespace + "'");
      }
   }

   @Override
   public @Nullable String getNamespace() {
      return this.namespace;
   }

   @Override
   public String @Nullable [] getConfigLocations() {
      return super.getConfigLocations();
   }

   public String getApplicationName() {
      return this.servletContext != null ? this.servletContext.getContextPath() : "";
   }

   protected ConfigurableEnvironment createEnvironment() {
      return new StandardServletEnvironment();
   }

   protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(this.servletContext, this.servletConfig));
      beanFactory.ignoreDependencyInterface(ServletContextAware.class);
      beanFactory.ignoreDependencyInterface(ServletConfigAware.class);
      WebApplicationContextUtils.registerWebApplicationScopes(beanFactory, this.servletContext);
      WebApplicationContextUtils.registerEnvironmentBeans(beanFactory, this.servletContext, this.servletConfig);
   }

   protected Resource getResourceByPath(String path) {
      Assert.state(this.servletContext != null, "No ServletContext available");
      return new ServletContextResource(this.servletContext, path);
   }

   protected ResourcePatternResolver getResourcePatternResolver() {
      return new ServletContextResourcePatternResolver(this);
   }

   protected void initPropertySources() {
      if (this.getEnvironment() instanceof ConfigurableWebEnvironment configurableWebEnv) {
         configurableWebEnv.initPropertySources(this.servletContext, this.servletConfig);
      }
   }
}
