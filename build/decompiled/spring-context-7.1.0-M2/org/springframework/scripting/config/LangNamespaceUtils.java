package org.springframework.scripting.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.scripting.support.ScriptFactoryPostProcessor;

@Deprecated(since = "7.0")
public abstract class LangNamespaceUtils {
   private static final String SCRIPT_FACTORY_POST_PROCESSOR_BEAN_NAME = "org.springframework.scripting.config.scriptFactoryPostProcessor";

   public static BeanDefinition registerScriptFactoryPostProcessorIfNecessary(BeanDefinitionRegistry registry) {
      BeanDefinition beanDefinition;
      if (registry.containsBeanDefinition("org.springframework.scripting.config.scriptFactoryPostProcessor")) {
         beanDefinition = registry.getBeanDefinition("org.springframework.scripting.config.scriptFactoryPostProcessor");
      } else {
         beanDefinition = new RootBeanDefinition(ScriptFactoryPostProcessor.class);
         registry.registerBeanDefinition("org.springframework.scripting.config.scriptFactoryPostProcessor", beanDefinition);
      }

      return beanDefinition;
   }
}
