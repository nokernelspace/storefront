package org.springframework.context;

@FunctionalInterface
public interface ApplicationContextInitializer<C extends ConfigurableApplicationContext> {
   void initialize(C applicationContext);
}
