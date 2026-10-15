package org.springframework.boot;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.AotDetector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.lang.Contract;

class DefaultApplicationContextFactory implements ApplicationContextFactory {
   @Override
   public @Nullable Class<? extends ConfigurableEnvironment> getEnvironmentType(@Nullable WebApplicationType webApplicationType) {
      return this.getFromSpringFactories(webApplicationType, ApplicationContextFactory::getEnvironmentType, null);
   }

   @Override
   public @Nullable ConfigurableEnvironment createEnvironment(@Nullable WebApplicationType webApplicationType) {
      return this.getFromSpringFactories(webApplicationType, ApplicationContextFactory::createEnvironment, null);
   }

   @Override
   public ConfigurableApplicationContext create(@Nullable WebApplicationType webApplicationType) {
      try {
         return this.getFromSpringFactories(webApplicationType, ApplicationContextFactory::create, this::createDefaultApplicationContext);
      } catch (Exception ex) {
         throw new IllegalStateException("Unable create a default ApplicationContext instance, you may need a custom ApplicationContextFactory", ex);
      }
   }

   private ConfigurableApplicationContext createDefaultApplicationContext() {
      return (ConfigurableApplicationContext)(!AotDetector.useGeneratedArtifacts() ? new AnnotationConfigApplicationContext() : new GenericApplicationContext());
   }

   @Contract("_, _, !null -> !null")
   private <T> @Nullable T getFromSpringFactories(
      @Nullable WebApplicationType webApplicationType,
      BiFunction<ApplicationContextFactory, @Nullable WebApplicationType, @Nullable T> action,
      @Nullable Supplier<T> defaultResult
   ) {
      for (ApplicationContextFactory candidate : SpringFactoriesLoader.loadFactories(ApplicationContextFactory.class, this.getClass().getClassLoader())) {
         T result = action.apply(candidate, webApplicationType);
         if (result != null) {
            return result;
         }
      }

      return defaultResult != null ? defaultResult.get() : null;
   }
}
