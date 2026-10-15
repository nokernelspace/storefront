package org.springframework.boot;

import org.springframework.core.env.ConfigurableEnvironment;

@FunctionalInterface
public interface EnvironmentPostProcessor {
   void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application);
}
