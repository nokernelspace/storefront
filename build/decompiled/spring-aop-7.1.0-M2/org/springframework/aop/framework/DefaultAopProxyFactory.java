package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import org.springframework.util.ClassUtils;

public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {
   public static final DefaultAopProxyFactory INSTANCE = new DefaultAopProxyFactory();
   private static final long serialVersionUID = 7930414337282325166L;

   @Override
   public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
      if (!config.isOptimize() && !config.isProxyTargetClass() && config.hasUserSuppliedInterfaces()) {
         return new JdkDynamicAopProxy(config);
      } else {
         Class<?> targetClass = config.getTargetClass();
         if (targetClass == null && config.getProxiedInterfaces().length == 0) {
            throw new AopConfigException("TargetSource cannot determine target class: Either an interface or a target is required for proxy creation.");
         } else {
            return targetClass != null && !targetClass.isInterface() && !Proxy.isProxyClass(targetClass) && !ClassUtils.isLambdaClass(targetClass)
               ? new ObjenesisCglibAopProxy(config)
               : new JdkDynamicAopProxy(config);
         }
      }
   }
}
