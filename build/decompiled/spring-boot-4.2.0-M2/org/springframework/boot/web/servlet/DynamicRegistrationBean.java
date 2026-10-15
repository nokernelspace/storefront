package org.springframework.boot.web.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.Registration.Dynamic;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.Conventions;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class DynamicRegistrationBean<D extends Dynamic> extends RegistrationBean implements BeanNameAware {
   private static final Log logger = LogFactory.getLog(RegistrationBean.class);
   private @Nullable String name;
   private boolean asyncSupported = true;
   private Map<String, String> initParameters = new LinkedHashMap<>();
   private @Nullable String beanName;
   private boolean ignoreRegistrationFailure;

   public void setName(String name) {
      Assert.hasLength(name, "'name' must not be empty");
      this.name = name;
   }

   public void setAsyncSupported(boolean asyncSupported) {
      this.asyncSupported = asyncSupported;
   }

   public boolean isAsyncSupported() {
      return this.asyncSupported;
   }

   public void setInitParameters(Map<String, String> initParameters) {
      Assert.notNull(initParameters, "'initParameters' must not be null");
      this.initParameters = new LinkedHashMap<>(initParameters);
   }

   public Map<String, String> getInitParameters() {
      return this.initParameters;
   }

   public void addInitParameter(String name, String value) {
      Assert.notNull(name, "'name' must not be null");
      this.initParameters.put(name, value);
   }

   @Override
   protected final void register(String description, ServletContext servletContext) {
      D registration = this.addRegistration(description, servletContext);
      if (registration == null) {
         if (this.ignoreRegistrationFailure) {
            logger.info(StringUtils.capitalize(description) + " was not registered (possibly already registered?)");
         } else {
            throw new IllegalStateException("Failed to register '%s' on the servlet context. Possibly already registered?".formatted(description));
         }
      } else {
         this.configure(registration);
      }
   }

   public void setIgnoreRegistrationFailure(boolean ignoreRegistrationFailure) {
      this.ignoreRegistrationFailure = ignoreRegistrationFailure;
   }

   public void setBeanName(String name) {
      this.beanName = name;
   }

   protected abstract @Nullable D addRegistration(String description, ServletContext servletContext);

   protected void configure(D registration) {
      registration.setAsyncSupported(this.asyncSupported);
      if (!this.initParameters.isEmpty()) {
         registration.setInitParameters(this.initParameters);
      }
   }

   protected final String getOrDeduceName(@Nullable Object value) {
      if (this.name != null) {
         return this.name;
      } else if (this.beanName != null) {
         return this.beanName;
      } else {
         return value == null ? "null" : Conventions.getVariableName(value);
      }
   }
}
