package org.springframework.aop.framework;

import org.aopalliance.intercept.Interceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.util.ClassUtils;

public class ProxyFactory extends ProxyCreatorSupport {
   public ProxyFactory() {
   }

   public ProxyFactory(Object target) {
      this.setTarget(target);
      this.setInterfaces(ClassUtils.getAllInterfaces(target));
   }

   public ProxyFactory(Class<?>... proxyInterfaces) {
      this.setInterfaces(proxyInterfaces);
   }

   public ProxyFactory(Class<?> proxyInterface, Interceptor interceptor) {
      this.addInterface(proxyInterface);
      this.addAdvice(interceptor);
   }

   public ProxyFactory(Class<?> proxyInterface, TargetSource targetSource) {
      this.addInterface(proxyInterface);
      this.setTargetSource(targetSource);
   }

   public Object getProxy() {
      return this.createAopProxy().getProxy();
   }

   public Object getProxy(@Nullable ClassLoader classLoader) {
      return this.createAopProxy().getProxy(classLoader);
   }

   public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
      return this.createAopProxy().getProxyClass(classLoader);
   }

   public static <T> T getProxy(Class<T> proxyInterface, Interceptor interceptor) {
      return (T)new ProxyFactory(proxyInterface, interceptor).getProxy();
   }

   public static <T> T getProxy(Class<T> proxyInterface, TargetSource targetSource) {
      return (T)new ProxyFactory(proxyInterface, targetSource).getProxy();
   }

   public static Object getProxy(TargetSource targetSource) {
      if (targetSource.getTargetClass() == null) {
         throw new IllegalArgumentException("Cannot create class proxy for TargetSource with null target class");
      }

      ProxyFactory proxyFactory = new ProxyFactory();
      proxyFactory.setTargetSource(targetSource);
      proxyFactory.setProxyTargetClass(true);
      return proxyFactory.getProxy();
   }
}
