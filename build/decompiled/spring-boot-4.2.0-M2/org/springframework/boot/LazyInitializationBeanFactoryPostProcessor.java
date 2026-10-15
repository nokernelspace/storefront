package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.Ordered;

public final class LazyInitializationBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {
   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      Collection<LazyInitializationExcludeFilter> filters = this.getFilters(beanFactory);

      for (String beanName : beanFactory.getBeanDefinitionNames()) {
         if (beanFactory.getBeanDefinition(beanName) instanceof AbstractBeanDefinition abstractBeanDefinition) {
            this.postProcess(beanFactory, filters, beanName, abstractBeanDefinition);
         }
      }
   }

   private Collection<LazyInitializationExcludeFilter> getFilters(ConfigurableListableBeanFactory beanFactory) {
      ArrayList<LazyInitializationExcludeFilter> filters = new ArrayList<>(
         beanFactory.getBeansOfType(LazyInitializationExcludeFilter.class, false, false).values()
      );
      filters.add(LazyInitializationExcludeFilter.forBeanTypes(SmartInitializingSingleton.class));
      filters.add(new LazyInitializationBeanFactoryPostProcessor.InfrastructureRoleLazyInitializationExcludeFilter());
      return filters;
   }

   private void postProcess(
      ConfigurableListableBeanFactory beanFactory, Collection<LazyInitializationExcludeFilter> filters, String beanName, AbstractBeanDefinition beanDefinition
   ) {
      Boolean lazyInit = beanDefinition.getLazyInit();
      if (lazyInit == null) {
         Class<?> beanType = this.getBeanType(beanFactory, beanName);
         if (!this.isExcluded(filters, beanName, beanDefinition, beanType)) {
            beanDefinition.setLazyInit(true);
         }
      }
   }

   private @Nullable Class<?> getBeanType(ConfigurableListableBeanFactory beanFactory, String beanName) {
      try {
         return beanFactory.getType(beanName, false);
      } catch (NoSuchBeanDefinitionException ex) {
         return null;
      }
   }

   private boolean isExcluded(
      Collection<LazyInitializationExcludeFilter> filters, String beanName, AbstractBeanDefinition beanDefinition, @Nullable Class<?> beanType
   ) {
      if (beanType != null) {
         for (LazyInitializationExcludeFilter filter : filters) {
            if (filter.isExcluded(beanName, beanDefinition, beanType)) {
               return true;
            }
         }
      }

      return false;
   }

   public int getOrder() {
      return Integer.MIN_VALUE;
   }

   private static final class InfrastructureRoleLazyInitializationExcludeFilter implements LazyInitializationExcludeFilter {
      @Override
      public boolean isExcluded(String beanName, BeanDefinition beanDefinition, Class<?> beanType) {
         return beanDefinition.getRole() == 2;
      }
   }
}
