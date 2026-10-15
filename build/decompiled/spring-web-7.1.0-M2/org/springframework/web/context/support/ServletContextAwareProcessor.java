package org.springframework.web.context.support;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

public class ServletContextAwareProcessor implements BeanPostProcessor {
   private @Nullable ServletContext servletContext;
   private @Nullable ServletConfig servletConfig;

   protected ServletContextAwareProcessor() {
   }

   public ServletContextAwareProcessor(ServletContext servletContext) {
      this(servletContext, null);
   }

   public ServletContextAwareProcessor(ServletConfig servletConfig) {
      this(null, servletConfig);
   }

   public ServletContextAwareProcessor(@Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig) {
      this.servletContext = servletContext;
      this.servletConfig = servletConfig;
   }

   protected @Nullable ServletContext getServletContext() {
      return this.servletContext == null && this.getServletConfig() != null ? this.getServletConfig().getServletContext() : this.servletContext;
   }

   protected @Nullable ServletConfig getServletConfig() {
      return this.servletConfig;
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      if (this.getServletContext() != null && bean instanceof ServletContextAware servletContextAware) {
         servletContextAware.setServletContext(this.getServletContext());
      }

      if (this.getServletConfig() != null && bean instanceof ServletConfigAware servletConfigAware) {
         servletConfigAware.setServletConfig(this.getServletConfig());
      }

      return bean;
   }

   public Object postProcessAfterInitialization(Object bean, String beanName) {
      return bean;
   }
}
