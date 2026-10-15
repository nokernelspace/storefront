package org.springframework.context.aot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

@FunctionalInterface
public interface AotApplicationContextInitializer<C extends ConfigurableApplicationContext> extends ApplicationContextInitializer<C> {
   static <C extends ConfigurableApplicationContext> AotApplicationContextInitializer<C> forInitializerClasses(String... initializerClassNames) {
      Assert.noNullElements(initializerClassNames, "'initializerClassNames' must not contain null elements");
      return applicationContext -> initialize(applicationContext, initializerClassNames);
   }

   private static <C extends ConfigurableApplicationContext> void initialize(C applicationContext, String... initializerClassNames) {
      Log logger = LogFactory.getLog(AotApplicationContextInitializer.class);
      ClassLoader classLoader = applicationContext.getClassLoader();
      logger.debug("Initializing ApplicationContext with AOT");

      for (String initializerClassName : initializerClassNames) {
         logger.trace(LogMessage.format("Applying %s", initializerClassName));
         instantiateInitializer(initializerClassName, classLoader).initialize(applicationContext);
      }
   }

   static <C extends ConfigurableApplicationContext> ApplicationContextInitializer<C> instantiateInitializer(
      String initializerClassName, @Nullable ClassLoader classLoader
   ) {
      try {
         Class<?> initializerClass = ClassUtils.resolveClassName(initializerClassName, classLoader);
         Assert.isAssignable(ApplicationContextInitializer.class, initializerClass);
         return (ApplicationContextInitializer<C>)BeanUtils.instantiateClass(initializerClass);
      } catch (BeanInstantiationException ex) {
         throw new IllegalArgumentException("Failed to instantiate ApplicationContextInitializer: " + initializerClassName, ex);
      }
   }
}
