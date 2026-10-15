package org.springframework.boot;

import org.springframework.beans.factory.config.BeanDefinition;

@FunctionalInterface
public interface LazyInitializationExcludeFilter {
   boolean isExcluded(String beanName, BeanDefinition beanDefinition, Class<?> beanType);

   static LazyInitializationExcludeFilter forBeanTypes(Class<?>... types) {
      return (beanName, beanDefinition, beanType) -> {
         for (Class<?> type : types) {
            if (type.isAssignableFrom(beanType)) {
               return true;
            }
         }

         return false;
      };
   }
}
