package org.springframework.boot.io;

import java.util.List;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ProtocolResolverApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
   public void initialize(ConfigurableApplicationContext applicationContext) {
      SpringFactoriesLoader loader = SpringFactoriesLoader.forDefaultResourceLocation(applicationContext.getClassLoader());
      List<ProtocolResolver> protocolResolvers = loader.load(ProtocolResolver.class);
      protocolResolvers.forEach(applicationContext::addProtocolResolver);
   }
}
