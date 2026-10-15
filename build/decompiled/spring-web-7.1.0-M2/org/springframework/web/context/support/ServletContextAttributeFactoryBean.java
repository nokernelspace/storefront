package org.springframework.web.context.support;

import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.web.context.ServletContextAware;

public class ServletContextAttributeFactoryBean implements FactoryBean<Object>, ServletContextAware {
   private @Nullable String attributeName;
   private @Nullable Object attribute;

   public void setAttributeName(String attributeName) {
      this.attributeName = attributeName;
   }

   @Override
   public void setServletContext(ServletContext servletContext) {
      if (this.attributeName == null) {
         throw new IllegalArgumentException("Property 'attributeName' is required");
      }

      this.attribute = servletContext.getAttribute(this.attributeName);
      if (this.attribute == null) {
         throw new IllegalStateException("No ServletContext attribute '" + this.attributeName + "' found");
      }
   }

   public @Nullable Object getObject() throws Exception {
      return this.attribute;
   }

   public @Nullable Class<?> getObjectType() {
      return this.attribute != null ? this.attribute.getClass() : null;
   }

   public boolean isSingleton() {
      return true;
   }
}
