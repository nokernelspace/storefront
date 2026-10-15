package org.springframework.web.context.support;

import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.web.context.ServletContextAware;

public class ServletContextParameterFactoryBean implements FactoryBean<String>, ServletContextAware {
   private @Nullable String initParamName;
   private @Nullable String paramValue;

   public void setInitParamName(String initParamName) {
      this.initParamName = initParamName;
   }

   @Override
   public void setServletContext(ServletContext servletContext) {
      if (this.initParamName == null) {
         throw new IllegalArgumentException("initParamName is required");
      }

      this.paramValue = servletContext.getInitParameter(this.initParamName);
      if (this.paramValue == null) {
         throw new IllegalStateException("No ServletContext init parameter '" + this.initParamName + "' found");
      }
   }

   public @Nullable String getObject() {
      return this.paramValue;
   }

   public Class<String> getObjectType() {
      return String.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
