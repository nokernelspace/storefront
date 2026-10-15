package org.springframework.context.annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.util.Assert;

public class FullyQualifiedAnnotationBeanNameGenerator extends AnnotationBeanNameGenerator {
   public static final FullyQualifiedAnnotationBeanNameGenerator INSTANCE = new FullyQualifiedAnnotationBeanNameGenerator();

   @Override
   protected String buildDefaultBeanName(BeanDefinition definition) {
      String beanClassName = definition.getBeanClassName();
      Assert.state(beanClassName != null, "No bean class name set");
      return beanClassName;
   }
}
