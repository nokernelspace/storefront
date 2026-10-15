package org.springframework.beans.factory;

import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.beans.BeansException;
import org.springframework.core.ParameterizedTypeReference;

public interface BeanRegistry {
   void register(BeanRegistrar registrar);

   void registerAlias(String name, String alias);

   <T> String registerBean(Class<T> beanClass);

   <T> String registerBean(ParameterizedTypeReference<T> beanType);

   <T> String registerBean(Class<T> beanClass, Consumer<BeanRegistry.Spec<T>> customizer);

   <T> String registerBean(ParameterizedTypeReference<T> beanType, Consumer<BeanRegistry.Spec<T>> customizer);

   <T> void registerBean(String name, Class<T> beanClass);

   <T> void registerBean(String name, ParameterizedTypeReference<T> beanType);

   <T> void registerBean(String name, Class<T> beanClass, Consumer<BeanRegistry.Spec<T>> customizer);

   <T> void registerBean(String name, ParameterizedTypeReference<T> beanType, Consumer<BeanRegistry.Spec<T>> customizer);

   boolean containsBean(String name);

   boolean containsBean(Class<?> beanType);

   <T> boolean containsBean(ParameterizedTypeReference<T> beanType);

   interface Spec<T> {
      BeanRegistry.Spec<T> backgroundInit();

      BeanRegistry.Spec<T> description(String description);

      BeanRegistry.Spec<T> fallback();

      BeanRegistry.Spec<T> infrastructure();

      BeanRegistry.Spec<T> lazyInit();

      BeanRegistry.Spec<T> notAutowirable();

      BeanRegistry.Spec<T> order(int order);

      BeanRegistry.Spec<T> primary();

      BeanRegistry.Spec<T> prototype();

      BeanRegistry.Spec<T> scope(String scope);

      BeanRegistry.Spec<T> supplier(Function<BeanRegistry.SupplierContext, T> supplier);
   }

   interface SupplierContext {
      <T> T bean(Class<T> beanClass) throws BeansException;

      <T> T bean(ParameterizedTypeReference<T> beanType) throws BeansException;

      <T> T bean(String name, Class<T> beanClass) throws BeansException;

      <T> ObjectProvider<T> beanProvider(Class<T> beanClass);

      <T> ObjectProvider<T> beanProvider(ParameterizedTypeReference<T> beanType);
   }
}
