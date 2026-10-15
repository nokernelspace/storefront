package org.springframework.boot;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

class ApplicationEnvironment extends StandardEnvironment {
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
