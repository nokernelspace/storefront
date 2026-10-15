package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.AliasRegistry;

public interface BeanDefinitionRegistry extends AliasRegistry {
   void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException;

   void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

   BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

   boolean containsBeanDefinition(String beanName);

   String[] getBeanDefinitionNames();

   int getBeanDefinitionCount();

   default boolean isBeanDefinitionOverridable(String beanName) {
      return true;
   }

   boolean isBeanNameInUse(String beanName);
}
