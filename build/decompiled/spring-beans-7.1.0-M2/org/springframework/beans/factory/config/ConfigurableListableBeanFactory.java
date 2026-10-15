package org.springframework.beans.factory.config;

import java.util.Iterator;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

public interface ConfigurableListableBeanFactory extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {
   void ignoreDependencyType(Class<?> type);

   void ignoreDependencyInterface(Class<?> ifc);

   void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue);

   boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor) throws NoSuchBeanDefinitionException;

   BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

   Iterator<String> getBeanNamesIterator();

   void clearMetadataCache();

   void freezeConfiguration();

   boolean isConfigurationFrozen();

   default void prepareSingletonBootstrap() {
   }

   void preInstantiateSingletons() throws BeansException;
}
