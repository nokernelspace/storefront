package org.springframework.context.weaving;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.util.Assert;

public class LoadTimeWeaverAwareProcessor implements BeanPostProcessor, BeanFactoryAware {
   private @Nullable LoadTimeWeaver loadTimeWeaver;
   private @Nullable BeanFactory beanFactory;

   public LoadTimeWeaverAwareProcessor() {
   }

   public LoadTimeWeaverAwareProcessor(@Nullable LoadTimeWeaver loadTimeWeaver) {
      this.loadTimeWeaver = loadTimeWeaver;
   }

   public LoadTimeWeaverAwareProcessor(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
      if (bean instanceof LoadTimeWeaverAware loadTimeWeaverAware) {
         LoadTimeWeaver ltw = this.loadTimeWeaver;
         if (ltw == null) {
            Assert.state(this.beanFactory != null, "BeanFactory required if no LoadTimeWeaver explicitly specified");
            ltw = (LoadTimeWeaver)this.beanFactory.getBean("loadTimeWeaver", LoadTimeWeaver.class);
         }

         loadTimeWeaverAware.setLoadTimeWeaver(ltw);
      }

      return bean;
   }

   public Object postProcessAfterInitialization(Object bean, String name) {
      return bean;
   }
}
