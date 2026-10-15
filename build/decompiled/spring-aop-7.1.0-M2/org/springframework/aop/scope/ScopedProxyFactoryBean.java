package org.springframework.aop.scope;

import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.aop.target.SimpleBeanTargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class ScopedProxyFactoryBean extends ProxyConfig implements FactoryBean<Object>, BeanFactoryAware, AopInfrastructureBean {
   private final SimpleBeanTargetSource scopedTargetSource = new SimpleBeanTargetSource();
   private @Nullable String targetBeanName;
   private @Nullable Object proxy;

   public ScopedProxyFactoryBean() {
      this.setProxyTargetClass(true);
   }

   public void setTargetBeanName(String targetBeanName) {
      this.targetBeanName = targetBeanName;
      this.scopedTargetSource.setTargetBeanName(targetBeanName);
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableBeanFactory cbf) {
         this.scopedTargetSource.setBeanFactory(beanFactory);
         ProxyFactory pf = new ProxyFactory();
         pf.copyFrom(this);
         pf.setTargetSource(this.scopedTargetSource);
         Assert.notNull(this.targetBeanName, "Property 'targetBeanName' is required");
         Class<?> beanType = beanFactory.getType(this.targetBeanName);
         if (beanType == null) {
            throw new IllegalStateException(
               "Cannot create scoped proxy for bean '" + this.targetBeanName + "': Target type could not be determined at the time of proxy creation."
            );
         }

         if (!this.isProxyTargetClass() || beanType.isInterface() || Modifier.isPrivate(beanType.getModifiers())) {
            pf.setInterfaces(ClassUtils.getAllInterfacesForClass(beanType, cbf.getBeanClassLoader()));
         }

         ScopedObject scopedObject = new DefaultScopedObject(cbf, this.scopedTargetSource.getTargetBeanName());
         pf.addAdvice(new DelegatingIntroductionInterceptor(scopedObject));
         pf.addInterface(AopInfrastructureBean.class);
         this.proxy = pf.getProxy(cbf.getBeanClassLoader());
      } else {
         throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
      }
   }

   public @Nullable Object getObject() {
      if (this.proxy == null) {
         throw new FactoryBeanNotInitializedException();
      } else {
         return this.proxy;
      }
   }

   public @Nullable Class<?> getObjectType() {
      return this.proxy != null ? this.proxy.getClass() : this.scopedTargetSource.getTargetClass();
   }

   public boolean isSingleton() {
      return true;
   }
}
