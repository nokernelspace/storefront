package org.springframework.web.jsf;

import jakarta.faces.application.NavigationHandler;
import jakarta.faces.context.FacesContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.web.context.WebApplicationContext;

public class DelegatingNavigationHandlerProxy extends NavigationHandler {
   public static final String DEFAULT_TARGET_BEAN_NAME = "jsfNavigationHandler";
   private @Nullable NavigationHandler originalNavigationHandler;

   public DelegatingNavigationHandlerProxy() {
   }

   public DelegatingNavigationHandlerProxy(NavigationHandler originalNavigationHandler) {
      this.originalNavigationHandler = originalNavigationHandler;
   }

   public void handleNavigation(FacesContext facesContext, String fromAction, String outcome) {
      NavigationHandler handler = this.getDelegate(facesContext);
      if (handler instanceof DecoratingNavigationHandler decoratingNavigationHandler) {
         decoratingNavigationHandler.handleNavigation(facesContext, fromAction, outcome, this.originalNavigationHandler);
      } else {
         handler.handleNavigation(facesContext, fromAction, outcome);
      }
   }

   protected NavigationHandler getDelegate(FacesContext facesContext) {
      String targetBeanName = this.getTargetBeanName(facesContext);
      return (NavigationHandler)this.getBeanFactory(facesContext).getBean(targetBeanName, NavigationHandler.class);
   }

   protected String getTargetBeanName(FacesContext facesContext) {
      return "jsfNavigationHandler";
   }

   protected BeanFactory getBeanFactory(FacesContext facesContext) {
      return this.getWebApplicationContext(facesContext);
   }

   protected WebApplicationContext getWebApplicationContext(FacesContext facesContext) {
      return FacesContextUtils.getRequiredWebApplicationContext(facesContext);
   }
}
