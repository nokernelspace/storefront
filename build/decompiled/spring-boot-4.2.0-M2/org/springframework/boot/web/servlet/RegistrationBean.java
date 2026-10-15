package org.springframework.boot.web.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

public abstract class RegistrationBean implements ServletContextInitializer, Ordered {
   private static final Log logger = LogFactory.getLog(RegistrationBean.class);
   private int order = Integer.MAX_VALUE;
   private boolean enabled = true;

   @Override
   public final void onStartup(ServletContext servletContext) throws ServletException {
      String description = this.getDescription();
      if (!this.isEnabled()) {
         logger.info(StringUtils.capitalize(description) + " was not registered (disabled)");
      } else {
         this.register(description, servletContext);
      }
   }

   protected abstract String getDescription();

   protected abstract void register(String description, ServletContext servletContext);

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }
}
