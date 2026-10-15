package org.springframework.context.config;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.context.annotation.AnnotationConfigBeanDefinitionParser;
import org.springframework.context.annotation.ComponentScanBeanDefinitionParser;

public class ContextNamespaceHandler extends NamespaceHandlerSupport {
   public void init() {
      this.registerBeanDefinitionParser("property-placeholder", new PropertyPlaceholderBeanDefinitionParser());
      this.registerBeanDefinitionParser("property-override", new PropertyOverrideBeanDefinitionParser());
      this.registerBeanDefinitionParser("annotation-config", new AnnotationConfigBeanDefinitionParser());
      this.registerBeanDefinitionParser("component-scan", new ComponentScanBeanDefinitionParser());
      this.registerBeanDefinitionParser("load-time-weaver", new LoadTimeWeaverBeanDefinitionParser());
      this.registerBeanDefinitionParser("spring-configured", new SpringConfiguredBeanDefinitionParser());
      this.registerBeanDefinitionParser("mbean-export", new MBeanExportBeanDefinitionParser());
      this.registerBeanDefinitionParser("mbean-server", new MBeanServerBeanDefinitionParser());
   }
}
