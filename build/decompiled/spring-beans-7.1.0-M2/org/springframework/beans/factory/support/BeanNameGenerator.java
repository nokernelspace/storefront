package org.springframework.beans.factory.support;

import org.springframework.beans.factory.config.BeanDefinition;

public interface BeanNameGenerator {
   String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry);
}
