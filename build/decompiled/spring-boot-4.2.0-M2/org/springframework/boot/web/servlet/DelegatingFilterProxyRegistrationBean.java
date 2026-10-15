package org.springframework.boot.web.servlet;

import jakarta.servlet.ServletException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;

public class DelegatingFilterProxyRegistrationBean extends AbstractFilterRegistrationBean<DelegatingFilterProxy> implements ApplicationContextAware {
   private ApplicationContext applicationContext;
   private final String targetBeanName;

   public DelegatingFilterProxyRegistrationBean(String targetBeanName, ServletRegistrationBean<?>... servletRegistrationBeans) {
      super(servletRegistrationBeans);
      Assert.hasLength(targetBeanName, "'targetBeanName' must not be empty");
      this.targetBeanName = targetBeanName;
      this.setName(targetBeanName);
   }

   public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
      this.applicationContext = applicationContext;
   }

   protected String getTargetBeanName() {
      return this.targetBeanName;
   }

   public DelegatingFilterProxy getFilter() {
      return new DelegatingFilterProxy(this.targetBeanName, this.getWebApplicationContext()) {
         protected void initFilterBean() throws ServletException {
         }
      };
   }

   private WebApplicationContext getWebApplicationContext() {
      Assert.state(this.applicationContext != null, "ApplicationContext has not been injected");
      Assert.state(this.applicationContext instanceof WebApplicationContext, "Injected ApplicationContext is not a WebApplicationContext");
      return (WebApplicationContext)this.applicationContext;
   }
}
