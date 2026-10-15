package org.springframework.boot.logging.log4j2;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.jspecify.annotations.Nullable;

@Plugin(name = "SpringBootConfigurationFactory", category = "ConfigurationFactory")
@Order(0)
public class SpringBootConfigurationFactory extends ConfigurationFactory {
   private static final String[] TYPES = new String[]{".springboot"};

   protected String[] getSupportedTypes() {
      return TYPES;
   }

   public @Nullable Configuration getConfiguration(LoggerContext loggerContext, @Nullable ConfigurationSource source) {
      return source != null && source != ConfigurationSource.NULL_SOURCE ? new DefaultConfiguration() : null;
   }
}
