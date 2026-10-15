package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {
   private static final long serialVersionUID = 5531744639992436476L;
   private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";
   private static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent(
      "kotlinx.coroutines.reactor.MonoKt", JdkDynamicAopProxy.class.getClassLoader()
   );
   private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);
   private final AdvisedSupport advised;
   private transient JdkDynamicAopProxy.ProxiedInterfacesCache cache;

   public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
      Assert.notNull(config, "AdvisedSupport must not be null");
      this.advised = config;
      JdkDynamicAopProxy.ProxiedInterfacesCache cache;
      if (config.proxyMetadataCache instanceof JdkDynamicAopProxy.ProxiedInterfacesCache proxiedInterfacesCache) {
         cache = proxiedInterfacesCache;
      } else {
         cache = new JdkDynamicAopProxy.ProxiedInterfacesCache(config);
         config.proxyMetadataCache = cache;
      }

      this.cache = cache;
   }

   @Override
   public Object getProxy() {
      return this.getProxy(ClassUtils.getDefaultClassLoader());
   }

   @Override
   public Object getProxy(@Nullable ClassLoader classLoader) {
      if (logger.isTraceEnabled()) {
         logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
      }

      return Proxy.newProxyInstance(this.determineClassLoader(classLoader), this.cache.proxiedInterfaces, this);
   }

   @Override
   public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
      return Proxy.getProxyClass(this.determineClassLoader(classLoader), this.cache.proxiedInterfaces);
   }

   private ClassLoader determineClassLoader(@Nullable ClassLoader classLoader) {
      if (classLoader == null) {
         return this.getClass().getClassLoader();
      }

      if (classLoader.getParent() == null) {
         ClassLoader aopClassLoader = this.getClass().getClassLoader();

         for (ClassLoader aopParent = aopClassLoader.getParent(); aopParent != null; aopParent = aopParent.getParent()) {
            if (classLoader == aopParent) {
               return aopClassLoader;
            }
         }
      }

      return classLoader;
   }

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      Object oldProxy = null;
      boolean setProxyContext = false;
      TargetSource targetSource = this.advised.targetSource;
      Object target = null;

      try {
         if (!this.cache.equalsDefined && AopUtils.isEqualsMethod(method)) {
            return this.equals(args[0]);
         }

         if (!this.cache.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
            return this.hashCode();
         }

         if (method.getDeclaringClass() == DecoratingProxy.class) {
            return AopProxyUtils.ultimateTargetClass(this.advised);
         }

         if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() && method.getDeclaringClass().isAssignableFrom(Advised.class)) {
            return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
         }

         if (this.advised.isExposeProxy()) {
            oldProxy = AopContext.setCurrentProxy(proxy);
            setProxyContext = true;
         }

         target = targetSource.getTarget();
         Class<?> targetClass = target != null ? target.getClass() : null;
         List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
         Object retVal;
         if (chain.isEmpty()) {
            Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
            retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
         } else {
            MethodInvocation invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
            retVal = invocation.proceed();
         }

         Class<?> returnType = method.getReturnType();
         if (retVal != null
            && retVal == target
            && returnType != Object.class
            && returnType.isInstance(proxy)
            && !RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
            retVal = proxy;
         } else if (retVal == null && returnType != void.class && returnType.isPrimitive()) {
            throw new AopInvocationException("Null return value from advice does not match primitive return type for: " + method);
         }

         if (COROUTINES_REACTOR_PRESENT && KotlinDetector.isSuspendingFunction(method)) {
            return "kotlinx.coroutines.flow.Flow".equals(new MethodParameter(method, -1).getParameterType().getName())
               ? CoroutinesUtils.asFlow(retVal)
               : CoroutinesUtils.awaitSingleOrNull(retVal, args[args.length - 1]);
         } else {
            return retVal;
         }
      } finally {
         if (target != null && !targetSource.isStatic()) {
            targetSource.releaseTarget(target);
         }

         if (setProxyContext) {
            AopContext.setCurrentProxy(oldProxy);
         }
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (other == this) {
         return true;
      }

      if (other == null) {
         return false;
      }

      JdkDynamicAopProxy otherProxy;
      if (other instanceof JdkDynamicAopProxy jdkDynamicAopProxy) {
         otherProxy = jdkDynamicAopProxy;
      } else {
         if (!Proxy.isProxyClass(other.getClass())) {
            return false;
         }

         if (!(Proxy.getInvocationHandler(other) instanceof JdkDynamicAopProxy jdkDynamicAopProxy)) {
            return false;
         }

         otherProxy = jdkDynamicAopProxy;
      }

      return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
   }

   @Override
   public int hashCode() {
      return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.cache = new JdkDynamicAopProxy.ProxiedInterfacesCache(this.advised);
   }

   private static final class ProxiedInterfacesCache {
      final Class<?>[] proxiedInterfaces;
      final boolean equalsDefined;
      final boolean hashCodeDefined;

      ProxiedInterfacesCache(AdvisedSupport config) {
         this.proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(config, true);
         boolean equalsDefined = false;
         boolean hashCodeDefined = false;

         for (Class<?> proxiedInterface : this.proxiedInterfaces) {
            Method[] methods = proxiedInterface.getDeclaredMethods();

            for (Method method : methods) {
               if (AopUtils.isEqualsMethod(method)) {
                  equalsDefined = true;
                  if (hashCodeDefined) {
                     break;
                  }
               }

               if (AopUtils.isHashCodeMethod(method)) {
                  hashCodeDefined = true;
                  if (equalsDefined) {
                     break;
                  }
               }
            }

            if (equalsDefined && hashCodeDefined) {
               break;
            }
         }

         this.equalsDefined = equalsDefined;
         this.hashCodeDefined = hashCodeDefined;
      }
   }
}
