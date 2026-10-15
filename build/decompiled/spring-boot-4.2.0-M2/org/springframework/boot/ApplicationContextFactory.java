package org.springframework.boot;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

@FunctionalInterface
public interface ApplicationContextFactory {
   ApplicationContextFactory DEFAULT = new DefaultApplicationContextFactory();

   default @Nullable Class<? extends ConfigurableEnvironment> getEnvironmentType(@Nullable WebApplicationType webApplicationType) {
      return null;
   }

   default @Nullable ConfigurableEnvironment createEnvironment(@Nullable WebApplicationType webApplicationType) {
      return null;
   }

   @Nullable ConfigurableApplicationContext create(@Nullable WebApplicationType webApplicationType);

   static ApplicationContextFactory ofContextClass(Class<? extends ConfigurableApplicationContext> contextClass) {
      return of(() -> (ConfigurableApplicationContext)BeanUtils.instantiateClass(contextClass));
   }

   static ApplicationContextFactory of(Supplier<ConfigurableApplicationContext> supplier) {
      return webApplicationType -> supplier.get();
   }
}
