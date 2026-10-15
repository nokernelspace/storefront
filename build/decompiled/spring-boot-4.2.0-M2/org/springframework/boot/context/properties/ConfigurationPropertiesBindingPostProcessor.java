package org.springframework.boot.context.properties;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.BindMethod;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.Assert;

public class ConfigurationPropertiesBindingPostProcessor implements BeanPostProcessor, PriorityOrdered, ApplicationContextAware, InitializingBean {
   public static final String BEAN_NAME = ConfigurationPropertiesBindingPostProcessor.class.getName();
   private ApplicationContext applicationContext;
   private BeanDefinitionRegistry registry;
   private ConfigurationPropertiesBinder binder;

   public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
      this.applicationContext = applicationContext;
   }

   public void afterPropertiesSet() throws Exception {
      this.registry = (BeanDefinitionRegistry)this.applicationContext.getAutowireCapableBeanFactory();
      this.binder = ConfigurationPropertiesBinder.get(this.applicationContext);
   }

   public int getOrder() {
      return -2147483647;
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      if (!this.hasBoundValueObject(beanName)) {
         this.bind(ConfigurationPropertiesBean.get(this.applicationContext, bean, beanName));
      }

      return bean;
   }

   private boolean hasBoundValueObject(String beanName) {
      return BindMethod.VALUE_OBJECT.equals(BindMethodAttribute.get(this.registry, beanName));
   }

   private void bind(@Nullable ConfigurationPropertiesBean bean) {
      if (bean != null) {
         Assert.state(
            bean.asBindTarget().getBindMethod() != BindMethod.VALUE_OBJECT,
            "Cannot bind @ConfigurationProperties for bean '" + bean.getName() + "'. Ensure that @ConstructorBinding has not been applied to regular bean"
         );

         try {
            this.binder.bind(bean);
         } catch (Exception ex) {
            throw new ConfigurationPropertiesBindException(bean, ex);
         }
      }
   }

   public static void register(BeanDefinitionRegistry registry) {
      Assert.notNull(registry, "'registry' must not be null");
      if (!registry.containsBeanDefinition(BEAN_NAME)) {
         BeanDefinition definition = BeanDefinitionBuilder.rootBeanDefinition(ConfigurationPropertiesBindingPostProcessor.class).getBeanDefinition();
         definition.setRole(2);
         registry.registerBeanDefinition(BEAN_NAME, definition);
      }

      ConfigurationPropertiesBinder.register(registry);
   }
}
