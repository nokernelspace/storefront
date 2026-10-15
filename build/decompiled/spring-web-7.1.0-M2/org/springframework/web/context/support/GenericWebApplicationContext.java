package org.springframework.web.context.support;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ServletContextAware;

public class GenericWebApplicationContext extends GenericApplicationContext implements ConfigurableWebApplicationContext {
   private @Nullable ServletContext servletContext;

   public GenericWebApplicationContext() {
   }

   public GenericWebApplicationContext(ServletContext servletContext) {
      this.servletContext = servletContext;
   }

   public GenericWebApplicationContext(DefaultListableBeanFactory beanFactory) {
      super(beanFactory);
   }

   public GenericWebApplicationContext(DefaultListableBeanFactory beanFactory, ServletContext servletContext) {
      super(beanFactory);
      this.servletContext = servletContext;
   }

   @Override
   public void setServletContext(@Nullable ServletContext servletContext) {
      this.servletContext = servletContext;
   }

   @Override
   public @Nullable ServletContext getServletContext() {
      return this.servletContext;
   }

   public String getApplicationName() {
      return this.servletContext != null ? this.servletContext.getContextPath() : "";
   }

   protected ConfigurableEnvironment createEnvironment() {
      return new StandardServletEnvironment();
   }

   protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      if (this.servletContext != null) {
         beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(this.servletContext));
         beanFactory.ignoreDependencyInterface(ServletContextAware.class);
      }

      WebApplicationContextUtils.registerWebApplicationScopes(beanFactory, this.servletContext);
      WebApplicationContextUtils.registerEnvironmentBeans(beanFactory, this.servletContext);
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
         configurableWebEnv.initPropertySources(this.servletContext, null);
      }
   }

   @Override
   public void setServletConfig(@Nullable ServletConfig servletConfig) {
   }

   @Override
   public @Nullable ServletConfig getServletConfig() {
      throw new UnsupportedOperationException("GenericWebApplicationContext does not support getServletConfig()");
   }

   @Override
   public void setNamespace(@Nullable String namespace) {
   }

   @Override
   public @Nullable String getNamespace() {
      throw new UnsupportedOperationException("GenericWebApplicationContext does not support getNamespace()");
   }

   @Override
   public void setConfigLocation(String configLocation) {
      if (StringUtils.hasText(configLocation)) {
         throw new UnsupportedOperationException(
            "GenericWebApplicationContext does not support setConfigLocation(). Do you still have a 'contextConfigLocation' init-param set?"
         );
      }
   }

   @Override
   public void setConfigLocations(String... configLocations) {
      if (!ObjectUtils.isEmpty(configLocations)) {
         throw new UnsupportedOperationException(
            "GenericWebApplicationContext does not support setConfigLocations(). Do you still have a 'contextConfigLocations' init-param set?"
         );
      }
   }

   @Override
   public String[] getConfigLocations() {
      throw new UnsupportedOperationException("GenericWebApplicationContext does not support getConfigLocations()");
   }
}
