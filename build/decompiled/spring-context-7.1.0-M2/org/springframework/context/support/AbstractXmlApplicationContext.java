package org.springframework.context.support;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.ResourceEntityResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

public abstract class AbstractXmlApplicationContext extends AbstractRefreshableConfigApplicationContext {
   private boolean validating = true;

   public AbstractXmlApplicationContext() {
   }

   public AbstractXmlApplicationContext(@Nullable ApplicationContext parent) {
      super(parent);
   }

   public void setValidating(boolean validating) {
      this.validating = validating;
   }

   @Override
   protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
      XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
      beanDefinitionReader.setEnvironment(this.getEnvironment());
      beanDefinitionReader.setResourceLoader(this);
      beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));
      this.initBeanDefinitionReader(beanDefinitionReader);
      this.loadBeanDefinitions(beanDefinitionReader);
   }

   protected void initBeanDefinitionReader(XmlBeanDefinitionReader reader) {
      reader.setValidating(this.validating);
   }

   protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws BeansException, IOException {
      Resource[] configResources = this.getConfigResources();
      if (configResources != null) {
         reader.loadBeanDefinitions(configResources);
      }

      String[] configLocations = this.getConfigLocations();
      if (configLocations != null) {
         reader.loadBeanDefinitions(configLocations);
      }
   }

   protected Resource @Nullable [] getConfigResources() {
      return null;
   }
}
