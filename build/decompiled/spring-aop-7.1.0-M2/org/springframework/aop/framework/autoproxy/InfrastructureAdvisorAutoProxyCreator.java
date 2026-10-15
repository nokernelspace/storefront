package org.springframework.aop.framework.autoproxy;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class InfrastructureAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator {
   private @Nullable ConfigurableListableBeanFactory beanFactory;

   @Override
   protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      super.initBeanFactory(beanFactory);
      this.beanFactory = beanFactory;
   }

   @Override
   protected boolean isEligibleAdvisorBean(String beanName) {
      return this.beanFactory != null && this.beanFactory.containsBeanDefinition(beanName) && this.beanFactory.getBeanDefinition(beanName).getRole() == 2;
   }
}
