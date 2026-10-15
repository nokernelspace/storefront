package org.springframework.boot.context.properties;

import org.springframework.boot.context.properties.bind.BindHandler;

@FunctionalInterface
public interface ConfigurationPropertiesBindHandlerAdvisor {
   BindHandler apply(BindHandler bindHandler);
}
