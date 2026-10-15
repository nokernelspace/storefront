package org.springframework.boot.web.context.reactive;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;

public class GenericReactiveWebApplicationContext extends GenericApplicationContext implements ConfigurableReactiveWebApplicationContext {
   public GenericReactiveWebApplicationContext() {
   }

   public GenericReactiveWebApplicationContext(DefaultListableBeanFactory beanFactory) {
      super(beanFactory);
   }

   protected ConfigurableEnvironment createEnvironment() {
      return new StandardReactiveWebEnvironment();
   }

   protected Resource getResourceByPath(String path) {
      return new FilteredReactiveWebContextResource(path);
   }
}
