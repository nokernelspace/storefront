package org.springframework.web.context.support;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.jndi.JndiLocatorDelegate;
import org.springframework.jndi.JndiPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.ConfigurableWebEnvironment;

public class StandardServletEnvironment extends StandardEnvironment implements ConfigurableWebEnvironment {
   public static final String SERVLET_CONTEXT_PROPERTY_SOURCE_NAME = "servletContextInitParams";
   public static final String SERVLET_CONFIG_PROPERTY_SOURCE_NAME = "servletConfigInitParams";
   public static final String JNDI_PROPERTY_SOURCE_NAME = "jndiProperties";
   private static final boolean JNDI_PRESENT = ClassUtils.isPresent("javax.naming.InitialContext", StandardServletEnvironment.class.getClassLoader());

   public StandardServletEnvironment() {
   }

   protected StandardServletEnvironment(MutablePropertySources propertySources) {
      super(propertySources);
   }

   protected void customizePropertySources(MutablePropertySources propertySources) {
      propertySources.addLast(new StubPropertySource("servletConfigInitParams"));
      propertySources.addLast(new StubPropertySource("servletContextInitParams"));
      if (JNDI_PRESENT && JndiLocatorDelegate.isDefaultJndiEnvironmentAvailable()) {
         propertySources.addLast(new JndiPropertySource("jndiProperties"));
      }

      super.customizePropertySources(propertySources);
   }

   @Override
   public void initPropertySources(@Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig) {
      WebApplicationContextUtils.initServletPropertySources(this.getPropertySources(), servletContext, servletConfig);
   }
}
