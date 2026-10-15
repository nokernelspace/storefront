package org.springframework.aop.framework;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

public class ProxyProcessorSupport extends ProxyConfig implements Ordered, BeanClassLoaderAware, AopInfrastructureBean {
   private int order = Integer.MAX_VALUE;
   private @Nullable ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();
   private boolean classLoaderConfigured = false;

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   public void setProxyClassLoader(@Nullable ClassLoader classLoader) {
      this.proxyClassLoader = classLoader;
      this.classLoaderConfigured = classLoader != null;
   }

   protected @Nullable ClassLoader getProxyClassLoader() {
      return this.proxyClassLoader;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      if (!this.classLoaderConfigured) {
         this.proxyClassLoader = classLoader;
      }
   }

   protected void evaluateProxyInterfaces(Class<?> beanClass, ProxyFactory proxyFactory) {
      Class<?>[] targetInterfaces = ClassUtils.getAllInterfacesForClass(beanClass, this.getProxyClassLoader());
      boolean hasReasonableProxyInterface = false;

      for (Class<?> ifc : targetInterfaces) {
         if (!this.isConfigurationCallbackInterface(ifc) && !this.isInternalLanguageInterface(ifc) && ifc.getMethods().length > 0) {
            hasReasonableProxyInterface = true;
            break;
         }
      }

      if (hasReasonableProxyInterface) {
         for (Class<?> ifc : targetInterfaces) {
            proxyFactory.addInterface(ifc);
         }
      } else {
         proxyFactory.setProxyTargetClass(true);
      }
   }

   protected boolean isConfigurationCallbackInterface(Class<?> ifc) {
      return AopProxyUtils.isConfigurationCallbackInterface(ifc);
   }

   protected boolean isInternalLanguageInterface(Class<?> ifc) {
      return ifc.getName().equals("groovy.lang.GroovyObject")
         || ifc.getName().endsWith(".cglib.proxy.Factory")
         || ifc.getName().endsWith(".bytebuddy.MockAccess");
   }
}
