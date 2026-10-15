package org.springframework.boot.bootstrap;

@FunctionalInterface
public interface BootstrapRegistryInitializer {
   void initialize(BootstrapRegistry registry);
}
