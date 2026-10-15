package org.springframework.aop.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.aspectj.AspectInstanceFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class SimpleBeanFactoryAwareAspectInstanceFactory implements AspectInstanceFactory, BeanFactoryAware {
   private @Nullable String aspectBeanName;
   private @Nullable BeanFactory beanFactory;

   public void setAspectBeanName(String aspectBeanName) {
      this.aspectBeanName = aspectBeanName;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      Assert.notNull(this.aspectBeanName, "'aspectBeanName' is required");
   }

   @Override
   public Object getAspectInstance() {
      Assert.state(this.beanFactory != null, "No BeanFactory set");
      Assert.state(this.aspectBeanName != null, "No 'aspectBeanName' set");
      return this.beanFactory.getBean(this.aspectBeanName);
   }

   @Override
   public @Nullable ClassLoader getAspectClassLoader() {
      return this.beanFactory instanceof ConfigurableBeanFactory cbf ? cbf.getBeanClassLoader() : ClassUtils.getDefaultClassLoader();
   }

   public int getOrder() {
      return this.beanFactory != null
            && this.aspectBeanName != null
            && this.beanFactory.isSingleton(this.aspectBeanName)
            && this.beanFactory.isTypeMatch(this.aspectBeanName, Ordered.class)
         ? ((Ordered)this.beanFactory.getBean(this.aspectBeanName)).getOrder()
         : Integer.MAX_VALUE;
   }
}
