package org.springframework.boot.web.context.reactive;

import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

public class StandardReactiveWebEnvironment extends StandardEnvironment implements ConfigurableReactiveWebEnvironment {
   public StandardReactiveWebEnvironment() {
   }

   protected StandardReactiveWebEnvironment(MutablePropertySources propertySources) {
      super(propertySources);
   }
}
