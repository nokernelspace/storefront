package org.springframework.beans.factory.config;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.util.ObjectUtils;

public abstract class PropertyResourceConfigurer extends PropertiesLoaderSupport implements BeanFactoryPostProcessor, PriorityOrdered {
   private int order = Integer.MAX_VALUE;

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      try {
         Properties mergedProps = this.mergeProperties();
         this.convertProperties(mergedProps);
         this.processProperties(beanFactory, mergedProps);
      } catch (IOException ex) {
         throw new BeanInitializationException("Could not load properties: " + ex.getMessage(), ex);
      }
   }

   protected void convertProperties(Properties props) {
      Enumeration<?> propertyNames = props.propertyNames();

      while (propertyNames.hasMoreElements()) {
         String propertyName = (String)propertyNames.nextElement();
         String propertyValue = props.getProperty(propertyName);
         String convertedValue = this.convertProperty(propertyName, propertyValue);
         if (!ObjectUtils.nullSafeEquals(propertyValue, convertedValue)) {
            props.setProperty(propertyName, convertedValue);
         }
      }
   }

   protected String convertProperty(String propertyName, String propertyValue) {
      return this.convertPropertyValue(propertyValue);
   }

   protected String convertPropertyValue(String originalValue) {
      return originalValue;
   }

   protected abstract void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) throws BeansException;
}
