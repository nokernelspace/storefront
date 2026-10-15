package org.springframework.boot.support;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class RandomValuePropertySourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
   public static final int ORDER = -2147483647;
   private final Log logger;

   public RandomValuePropertySourceEnvironmentPostProcessor(DeferredLogFactory logFactory) {
      this.logger = logFactory.getLog(RandomValuePropertySourceEnvironmentPostProcessor.class);
   }

   public int getOrder() {
      return -2147483647;
   }

   @Override
   public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
      RandomValuePropertySource.addToEnvironment(environment, this.logger);
   }
}
