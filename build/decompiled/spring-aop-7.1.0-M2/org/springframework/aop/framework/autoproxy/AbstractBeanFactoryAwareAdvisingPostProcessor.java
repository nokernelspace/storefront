package org.springframework.aop.framework.autoproxy;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AbstractAdvisingBeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public abstract class AbstractBeanFactoryAwareAdvisingPostProcessor extends AbstractAdvisingBeanPostProcessor implements BeanFactoryAware {
   protected @Nullable ConfigurableListableBeanFactory beanFactory;

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory instanceof ConfigurableListableBeanFactory clbf ? clbf : null;
      AutoProxyUtils.applyDefaultProxyConfig(this, beanFactory);
   }

   @Override
   protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
      if (this.beanFactory != null) {
         AutoProxyUtils.exposeTargetClass(this.beanFactory, beanName, bean.getClass());
      }

      ProxyFactory proxyFactory = super.prepareProxyFactory(bean, beanName);
      if (this.beanFactory != null) {
         if (AutoProxyUtils.shouldProxyTargetClass(this.beanFactory, beanName)) {
            proxyFactory.setProxyTargetClass(true);
         } else {
            Class<?>[] ifcs = AutoProxyUtils.determineExposedInterfaces(this.beanFactory, beanName);
            if (ifcs != null) {
               proxyFactory.setProxyTargetClass(false);

               for (Class<?> ifc : ifcs) {
                  proxyFactory.addInterface(ifc);
               }
            }
         }
      }

      return proxyFactory;
   }

   @Override
   protected boolean isEligible(Object bean, String beanName) {
      return !AutoProxyUtils.isOriginalInstance(beanName, bean.getClass()) && super.isEligible(bean, beanName);
   }
}
