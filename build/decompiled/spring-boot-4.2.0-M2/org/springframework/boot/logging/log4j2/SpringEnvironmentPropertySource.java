package org.springframework.boot.logging.log4j2;

import org.apache.logging.log4j.util.PropertySource;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

class SpringEnvironmentPropertySource implements PropertySource {
   private static final int PRIORITY = -100;
   private volatile @Nullable Environment environment;

   public int getPriority() {
      return -100;
   }

   public @Nullable String getProperty(String key) {
      Environment environment = this.environment;
      return environment != null ? environment.getProperty(key) : null;
   }

   public boolean containsProperty(String key) {
      Environment environment = this.environment;
      return environment != null && environment.containsProperty(key);
   }

   void setEnvironment(@Nullable Environment environment) {
      this.environment = environment;
   }
}
