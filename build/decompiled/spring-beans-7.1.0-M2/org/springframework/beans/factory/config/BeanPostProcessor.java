package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;

public interface BeanPostProcessor {
   default @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      return bean;
   }

   default @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
      return bean;
   }
}
