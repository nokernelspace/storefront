package org.springframework.beans.factory.config;

@FunctionalInterface
public interface BeanDefinitionCustomizer {
   void customize(BeanDefinition bd);
}
