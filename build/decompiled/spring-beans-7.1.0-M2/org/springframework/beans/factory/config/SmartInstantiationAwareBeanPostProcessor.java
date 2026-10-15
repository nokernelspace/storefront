package org.springframework.beans.factory.config;

import java.lang.reflect.Constructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;

public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {
   default @Nullable Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
      return null;
   }

   default Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {
      return beanClass;
   }

   default Constructor<?> @Nullable [] determineCandidateConstructors(Class<?> beanClass, String beanName) throws BeansException {
      return null;
   }

   default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
      return bean;
   }
}
