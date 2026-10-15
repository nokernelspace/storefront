package org.springframework.boot.web.context.reactive;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;

public class AnnotationConfigReactiveWebApplicationContext extends AnnotationConfigApplicationContext implements ConfigurableReactiveWebApplicationContext {
   public AnnotationConfigReactiveWebApplicationContext() {
   }

   public AnnotationConfigReactiveWebApplicationContext(DefaultListableBeanFactory beanFactory) {
      super(beanFactory);
   }

   public AnnotationConfigReactiveWebApplicationContext(Class<?>... annotatedClasses) {
      super(annotatedClasses);
   }

   public AnnotationConfigReactiveWebApplicationContext(String... basePackages) {
      super(basePackages);
   }

   protected ConfigurableEnvironment createEnvironment() {
      return new StandardReactiveWebEnvironment();
   }

   protected Resource getResourceByPath(String path) {
      return new FilteredReactiveWebContextResource(path);
   }
}
