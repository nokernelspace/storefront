package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

public interface ConditionContext {
   BeanDefinitionRegistry getRegistry();

   @Nullable ConfigurableListableBeanFactory getBeanFactory();

   Environment getEnvironment();

   ResourceLoader getResourceLoader();

   @Nullable ClassLoader getClassLoader();
}
