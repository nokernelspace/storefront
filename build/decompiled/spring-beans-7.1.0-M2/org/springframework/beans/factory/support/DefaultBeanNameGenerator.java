package org.springframework.beans.factory.support;

import org.springframework.beans.factory.config.BeanDefinition;

public class DefaultBeanNameGenerator implements BeanNameGenerator {
   public static final DefaultBeanNameGenerator INSTANCE = new DefaultBeanNameGenerator();

   @Override
   public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
      return BeanDefinitionReaderUtils.generateBeanName(definition, registry);
   }
}
