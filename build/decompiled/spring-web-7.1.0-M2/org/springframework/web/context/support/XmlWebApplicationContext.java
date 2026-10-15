package org.springframework.web.context.support;

import java.io.IOException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.ResourceEntityResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

public class XmlWebApplicationContext extends AbstractRefreshableWebApplicationContext {
   public static final String DEFAULT_CONFIG_LOCATION = "/WEB-INF/applicationContext.xml";
   public static final String DEFAULT_CONFIG_LOCATION_PREFIX = "/WEB-INF/";
   public static final String DEFAULT_CONFIG_LOCATION_SUFFIX = ".xml";

   protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
      XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
      beanDefinitionReader.setEnvironment(this.getEnvironment());
      beanDefinitionReader.setResourceLoader(this);
      beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));
      this.initBeanDefinitionReader(beanDefinitionReader);
      this.loadBeanDefinitions(beanDefinitionReader);
   }

   protected void initBeanDefinitionReader(XmlBeanDefinitionReader beanDefinitionReader) {
   }

   protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws IOException {
      String[] configLocations = this.getConfigLocations();
      if (configLocations != null) {
         for (String configLocation : configLocations) {
            reader.loadBeanDefinitions(configLocation);
         }
      }
   }

   protected String[] getDefaultConfigLocations() {
      return this.getNamespace() != null ? new String[]{"/WEB-INF/" + this.getNamespace() + ".xml"} : new String[]{"/WEB-INF/applicationContext.xml"};
   }
}
