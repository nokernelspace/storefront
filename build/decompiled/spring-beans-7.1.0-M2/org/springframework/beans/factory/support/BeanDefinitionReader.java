package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public interface BeanDefinitionReader {
   BeanDefinitionRegistry getRegistry();

   @Nullable ResourceLoader getResourceLoader();

   @Nullable ClassLoader getBeanClassLoader();

   BeanNameGenerator getBeanNameGenerator();

   int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException;

   int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException;

   int loadBeanDefinitions(String location) throws BeanDefinitionStoreException;

   int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException;
}
