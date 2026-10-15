package org.springframework.boot.support;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.io.support.SpringFactoriesLoader;

@FunctionalInterface
public interface EnvironmentPostProcessorsFactory {
   List<EnvironmentPostProcessor> getEnvironmentPostProcessors(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext);

   static EnvironmentPostProcessorsFactory fromSpringFactories(@Nullable ClassLoader classLoader) {
      return new SpringFactoriesEnvironmentPostProcessorsFactory(SpringFactoriesLoader.forDefaultResourceLocation(classLoader));
   }

   static EnvironmentPostProcessorsFactory of(Class<?>... classes) {
      return new ReflectionEnvironmentPostProcessorsFactory(classes);
   }

   static EnvironmentPostProcessorsFactory of(String... classNames) {
      return of(null, classNames);
   }

   static EnvironmentPostProcessorsFactory of(@Nullable ClassLoader classLoader, String... classNames) {
      return new ReflectionEnvironmentPostProcessorsFactory(classLoader, classNames);
   }
}
