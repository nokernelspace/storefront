package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

public interface BeanFactory {
   String FACTORY_BEAN_PREFIX = "&";
   char FACTORY_BEAN_PREFIX_CHAR = '&';

   Object getBean(String name) throws BeansException;

   <T> T getBean(String name, Class<T> requiredType) throws BeansException;

   <T> T getBean(String name, ParameterizedTypeReference<T> typeReference) throws BeansException;

   Object getBean(String name, @Nullable Object @Nullable ... args) throws BeansException;

   <T> T getBean(Class<T> requiredType) throws BeansException;

   <T> T getBean(Class<T> requiredType, @Nullable Object @Nullable ... args) throws BeansException;

   <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

   <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

   <T> ObjectProvider<T> getBeanProvider(ParameterizedTypeReference<T> requiredType);

   boolean containsBean(String name);

   boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

   boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

   boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

   boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

   @Nullable Class<?> getType(String name) throws NoSuchBeanDefinitionException;

   @Nullable Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

   String[] getAliases(String name);
}
