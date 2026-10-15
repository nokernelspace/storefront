package org.springframework.boot.web.context.servlet;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.web.context.support.StandardServletEnvironment;

public class ApplicationServletEnvironment extends StandardServletEnvironment {
   protected @Nullable String doGetActiveProfilesProperty() {
      return null;
   }

   protected @Nullable String doGetDefaultProfilesProperty() {
      return null;
   }

   protected ConfigurablePropertyResolver createPropertyResolver(MutablePropertySources propertySources) {
      return ConfigurationPropertySources.createPropertyResolver(propertySources);
   }
}
