package org.springframework.jmx.access;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.MBeanServerNotFoundException;
import org.springframework.util.ClassUtils;

public class MBeanProxyFactoryBean extends MBeanClientInterceptor implements FactoryBean<Object>, BeanClassLoaderAware, InitializingBean {
   private @Nullable Class<?> proxyInterface;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable Object mbeanProxy;

   public void setProxyInterface(Class<?> proxyInterface) {
      this.proxyInterface = proxyInterface;
   }

   @Override
   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   public void afterPropertiesSet() throws MBeanServerNotFoundException, MBeanInfoRetrievalException {
      super.afterPropertiesSet();
      Class<?> interfaceToUse;
      if (this.proxyInterface == null) {
         interfaceToUse = this.getManagementInterface();
         if (interfaceToUse == null) {
            throw new IllegalArgumentException("Property 'proxyInterface' or 'managementInterface' is required");
         }

         this.proxyInterface = interfaceToUse;
      } else {
         interfaceToUse = this.proxyInterface;
         if (this.getManagementInterface() == null) {
            this.setManagementInterface(interfaceToUse);
         }
      }

      this.mbeanProxy = new ProxyFactory(interfaceToUse, this).getProxy(this.beanClassLoader);
   }

   public @Nullable Object getObject() {
      return this.mbeanProxy;
   }

   public @Nullable Class<?> getObjectType() {
      return this.proxyInterface;
   }

   public boolean isSingleton() {
      return true;
   }
}
