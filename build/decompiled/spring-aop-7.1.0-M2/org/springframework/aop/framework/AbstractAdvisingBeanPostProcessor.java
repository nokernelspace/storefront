package org.springframework.aop.framework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.SmartClassLoader;

public abstract class AbstractAdvisingBeanPostProcessor extends ProxyProcessorSupport implements SmartInstantiationAwareBeanPostProcessor {
   protected @Nullable Advisor advisor;
   protected boolean beforeExistingAdvisors = false;
   private final Map<Class<?>, Boolean> eligibleBeans = new ConcurrentHashMap<>(256);

   public void setBeforeExistingAdvisors(boolean beforeExistingAdvisors) {
      this.beforeExistingAdvisors = beforeExistingAdvisors;
   }

   public Class<?> determineBeanType(Class<?> beanClass, String beanName) {
      if (this.advisor != null && this.isEligible(beanClass)) {
         ProxyFactory proxyFactory = new ProxyFactory();
         proxyFactory.copyFrom(this);
         proxyFactory.setTargetClass(beanClass);
         if (!proxyFactory.isProxyTargetClass()) {
            this.evaluateProxyInterfaces(beanClass, proxyFactory);
         }

         proxyFactory.addAdvisor(this.advisor);
         this.customizeProxyFactory(proxyFactory);
         ClassLoader classLoader = this.getProxyClassLoader();
         if (classLoader instanceof SmartClassLoader smartClassLoader && classLoader != beanClass.getClassLoader()) {
            classLoader = smartClassLoader.getOriginalClassLoader();
         }

         return proxyFactory.getProxyClass(classLoader);
      } else {
         return beanClass;
      }
   }

   public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (this.advisor != null && !(bean instanceof AopInfrastructureBean)) {
         if (bean instanceof Advised advised && !advised.isFrozen() && this.isEligible(AopUtils.getTargetClass(bean))) {
            if (this.beforeExistingAdvisors) {
               advised.addAdvisor(0, this.advisor);
            } else {
               if (advised.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE && advised.getAdvisorCount() > 0) {
                  advised.addAdvisor(advised.getAdvisorCount() - 1, this.advisor);
                  return bean;
               }

               advised.addAdvisor(this.advisor);
            }

            return bean;
         } else if (this.isEligible(bean, beanName)) {
            ProxyFactory proxyFactory = this.prepareProxyFactory(bean, beanName);
            if (!proxyFactory.isProxyTargetClass() && !proxyFactory.hasUserSuppliedInterfaces()) {
               this.evaluateProxyInterfaces(bean.getClass(), proxyFactory);
            }

            proxyFactory.addAdvisor(this.advisor);
            this.customizeProxyFactory(proxyFactory);
            proxyFactory.setFrozen(this.isFrozen());
            proxyFactory.setPreFiltered(true);
            ClassLoader classLoader = this.getProxyClassLoader();
            if (classLoader instanceof SmartClassLoader smartClassLoader && classLoader != bean.getClass().getClassLoader()) {
               classLoader = smartClassLoader.getOriginalClassLoader();
            }

            return proxyFactory.getProxy(classLoader);
         } else {
            return bean;
         }
      } else {
         return bean;
      }
   }

   protected boolean isEligible(Object bean, String beanName) {
      return this.isEligible(bean.getClass());
   }

   protected boolean isEligible(Class<?> targetClass) {
      Boolean eligible = this.eligibleBeans.get(targetClass);
      if (eligible != null) {
         return eligible;
      }

      if (this.advisor == null) {
         return false;
      }

      eligible = AopUtils.canApply(this.advisor, targetClass);
      this.eligibleBeans.put(targetClass, eligible);
      return eligible;
   }

   protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
      ProxyFactory proxyFactory = new ProxyFactory();
      proxyFactory.copyFrom(this);
      proxyFactory.setFrozen(false);
      proxyFactory.setTarget(bean);
      return proxyFactory;
   }

   protected void customizeProxyFactory(ProxyFactory proxyFactory) {
   }
}
