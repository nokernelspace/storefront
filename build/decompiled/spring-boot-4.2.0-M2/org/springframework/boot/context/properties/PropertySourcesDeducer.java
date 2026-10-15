package org.springframework.boot.context.properties;

import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySources;
import org.springframework.util.Assert;

class PropertySourcesDeducer {
   private static final Log logger = LogFactory.getLog(PropertySourcesDeducer.class);
   private final ApplicationContext applicationContext;

   PropertySourcesDeducer(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   PropertySources getPropertySources() {
      PropertySourcesPlaceholderConfigurer configurer = this.getSinglePropertySourcesPlaceholderConfigurer();
      if (configurer != null) {
         return configurer.getAppliedPropertySources();
      }

      MutablePropertySources sources = this.extractEnvironmentPropertySources();
      Assert.state(sources != null, "Unable to obtain PropertySources from PropertySourcesPlaceholderConfigurer or Environment");
      return sources;
   }

   private @Nullable PropertySourcesPlaceholderConfigurer getSinglePropertySourcesPlaceholderConfigurer() {
      Map<String, PropertySourcesPlaceholderConfigurer> beans = this.applicationContext
         .getBeansOfType(PropertySourcesPlaceholderConfigurer.class, false, false);
      if (beans.size() == 1) {
         return beans.values().iterator().next();
      }

      if (beans.size() > 1 && logger.isWarnEnabled()) {
         logger.warn("Multiple PropertySourcesPlaceholderConfigurer beans registered " + beans.keySet() + ", falling back to Environment");
      }

      return null;
   }

   private @Nullable MutablePropertySources extractEnvironmentPropertySources() {
      return this.applicationContext.getEnvironment() instanceof ConfigurableEnvironment configurableEnvironment
         ? configurableEnvironment.getPropertySources()
         : null;
   }
}
