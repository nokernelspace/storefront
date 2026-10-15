package org.springframework.beans.factory.aot;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

@FunctionalInterface
public interface BeanFactoryInitializationAotProcessor {
   @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory);
}
