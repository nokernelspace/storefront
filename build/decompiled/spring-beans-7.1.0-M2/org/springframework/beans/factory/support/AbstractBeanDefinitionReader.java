package org.springframework.beans.factory.support;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;

public abstract class AbstractBeanDefinitionReader implements BeanDefinitionReader, EnvironmentCapable {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final BeanDefinitionRegistry registry;
   private @Nullable ResourceLoader resourceLoader;
   private @Nullable ClassLoader beanClassLoader;
   private Environment environment;
   private BeanNameGenerator beanNameGenerator = DefaultBeanNameGenerator.INSTANCE;

   protected AbstractBeanDefinitionReader(BeanDefinitionRegistry registry) {
      Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
      this.registry = registry;
      if (this.registry instanceof ResourceLoader _resourceLoader) {
         this.resourceLoader = _resourceLoader;
      } else {
         this.resourceLoader = new PathMatchingResourcePatternResolver();
      }

      if (this.registry instanceof EnvironmentCapable environmentCapable) {
         this.environment = environmentCapable.getEnvironment();
      } else {
         this.environment = new StandardEnvironment();
      }
   }

   @Override
   public final BeanDefinitionRegistry getRegistry() {
      return this.registry;
   }

   public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
      this.resourceLoader = resourceLoader;
   }

   @Override
   public @Nullable ResourceLoader getResourceLoader() {
      return this.resourceLoader;
   }

   public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   @Override
   public @Nullable ClassLoader getBeanClassLoader() {
      return this.beanClassLoader;
   }

   public void setEnvironment(Environment environment) {
      Assert.notNull(environment, "Environment must not be null");
      this.environment = environment;
   }

   public Environment getEnvironment() {
      return this.environment;
   }

   public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
      this.beanNameGenerator = beanNameGenerator != null ? beanNameGenerator : DefaultBeanNameGenerator.INSTANCE;
   }

   @Override
   public BeanNameGenerator getBeanNameGenerator() {
      return this.beanNameGenerator;
   }

   @Override
   public int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException {
      Assert.notNull(resources, "Resource array must not be null");
      int count = 0;

      for (Resource resource : resources) {
         count += this.loadBeanDefinitions(resource);
      }

      return count;
   }

   @Override
   public int loadBeanDefinitions(String location) throws BeanDefinitionStoreException {
      return this.loadBeanDefinitions(location, null);
   }

   public int loadBeanDefinitions(String location, @Nullable Set<Resource> actualResources) throws BeanDefinitionStoreException {
      ResourceLoader resourceLoader = this.getResourceLoader();
      if (resourceLoader == null) {
         throw new BeanDefinitionStoreException("Cannot load bean definitions from location [" + location + "]: no ResourceLoader available");
      }

      if (resourceLoader instanceof ResourcePatternResolver resourcePatternResolver) {
         try {
            Resource[] resources = resourcePatternResolver.getResources(location);
            int count = this.loadBeanDefinitions(resources);
            if (actualResources != null) {
               Collections.addAll(actualResources, resources);
            }

            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Loaded " + count + " bean definitions from location pattern [" + location + "]");
            }

            return count;
         } catch (IOException ex) {
            throw new BeanDefinitionStoreException("Could not resolve bean definition resource pattern [" + location + "]", ex);
         }
      } else {
         Resource resource = resourceLoader.getResource(location);
         int count = this.loadBeanDefinitions(resource);
         if (actualResources != null) {
            actualResources.add(resource);
         }

         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Loaded " + count + " bean definitions from location [" + location + "]");
         }

         return count;
      }
   }

   @Override
   public int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException {
      Assert.notNull(locations, "Location array must not be null");
      int count = 0;

      for (String location : locations) {
         count += this.loadBeanDefinitions(location);
      }

      return count;
   }
}
