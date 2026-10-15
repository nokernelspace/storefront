package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.aot.AotDetector;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.GeneratorStrategy;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Dispatcher;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.cglib.transform.impl.UndeclaredThrowableStrategy;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.SmartClassLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

class CglibAopProxy implements AopProxy, Serializable {
   private static final int AOP_PROXY = 0;
   private static final int INVOKE_TARGET = 1;
   private static final int NO_OVERRIDE = 2;
   private static final int DISPATCH_TARGET = 3;
   private static final int DISPATCH_ADVISED = 4;
   private static final int INVOKE_EQUALS = 5;
   private static final int INVOKE_HASHCODE = 6;
   private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";
   private static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt", CglibAopProxy.class.getClassLoader());
   private static final GeneratorStrategy undeclaredThrowableStrategy = new UndeclaredThrowableStrategy(UndeclaredThrowableException.class);
   protected static final Log logger = LogFactory.getLog(CglibAopProxy.class);
   private static final Map<Class<?>, Boolean> validatedClasses = new WeakHashMap<>();
   protected final AdvisedSupport advised;
   protected Object @Nullable [] constructorArgs;
   protected Class<?> @Nullable [] constructorArgTypes;
   private final transient CglibAopProxy.AdvisedDispatcher advisedDispatcher;
   private transient Map<Method, Integer> fixedInterceptorMap = Collections.emptyMap();
   private transient int fixedInterceptorOffset;

   public CglibAopProxy(AdvisedSupport config) throws AopConfigException {
      Assert.notNull(config, "AdvisedSupport must not be null");
      this.advised = config;
      this.advisedDispatcher = new CglibAopProxy.AdvisedDispatcher(this.advised);
   }

   public void setConstructorArguments(Object @Nullable [] constructorArgs, Class<?> @Nullable [] constructorArgTypes) {
      if (constructorArgs != null && constructorArgTypes != null) {
         if (constructorArgs.length != constructorArgTypes.length) {
            throw new IllegalArgumentException(
               "Number of 'constructorArgs' (" + constructorArgs.length + ") must match number of 'constructorArgTypes' (" + constructorArgTypes.length + ")"
            );
         }

         this.constructorArgs = constructorArgs;
         this.constructorArgTypes = constructorArgTypes;
      } else {
         throw new IllegalArgumentException("Both 'constructorArgs' and 'constructorArgTypes' need to be specified");
      }
   }

   @Override
   public Object getProxy() {
      return this.buildProxy(null, false);
   }

   @Override
   public Object getProxy(@Nullable ClassLoader classLoader) {
      return this.buildProxy(classLoader, false);
   }

   @Override
   public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
      return (Class<?>)this.buildProxy(classLoader, true);
   }

   private Object buildProxy(@Nullable ClassLoader classLoader, boolean classOnly) {
      if (logger.isTraceEnabled()) {
         logger.trace("Creating CGLIB proxy: " + this.advised.getTargetSource());
      }

      try {
         Class<?> rootClass = this.advised.getTargetClass();
         Assert.state(rootClass != null, "Target class must be available for creating a CGLIB proxy");
         Class<?> proxySuperClass = rootClass;
         if (rootClass.getName().contains("$$")) {
            proxySuperClass = rootClass.getSuperclass();
            Class<?>[] additionalInterfaces = rootClass.getInterfaces();

            for (Class<?> additionalInterface : additionalInterfaces) {
               this.advised.addInterface(additionalInterface);
            }
         }

         this.validateClassIfNecessary(proxySuperClass, classLoader);
         Enhancer enhancer = this.createEnhancer();
         if (classLoader != null) {
            enhancer.setClassLoader(classLoader);
            if (classLoader instanceof SmartClassLoader smartClassLoader && smartClassLoader.isClassReloadable(proxySuperClass)) {
               enhancer.setUseCache(false);
            }
         }

         enhancer.setSuperclass(proxySuperClass);
         enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised));
         enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
         enhancer.setAttemptLoad(enhancer.getUseCache() && AotDetector.useGeneratedArtifacts());
         enhancer.setStrategy(
            KotlinDetector.isKotlinType(proxySuperClass)
               ? new ClassLoaderAwareGeneratorStrategy(classLoader)
               : new ClassLoaderAwareGeneratorStrategy(classLoader, undeclaredThrowableStrategy)
         );
         Callback[] callbacks = this.getCallbacks(rootClass);
         Class<?>[] types = new Class[callbacks.length];

         for (int x = 0; x < types.length; x++) {
            types[x] = callbacks[x].getClass();
         }

         CglibAopProxy.ProxyCallbackFilter filter = new CglibAopProxy.ProxyCallbackFilter(
            this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset
         );
         enhancer.setCallbackFilter(filter);
         enhancer.setCallbackTypes(types);

         try {
            return classOnly ? this.createProxyClass(enhancer) : this.createProxyClassAndInstance(enhancer, callbacks);
         } finally {
            filter.advised.reduceToAdvisorKey();
         }
      } catch (CodeGenerationException | IllegalArgumentException ex) {
         throw new AopConfigException(
            "Could not generate CGLIB subclass of "
               + this.advised.getTargetClass()
               + ": Common causes of this problem include using a final class or a non-visible class",
            ex
         );
      } catch (Throwable ex) {
         throw new AopConfigException("Unexpected AOP exception", ex);
      }
   }

   protected Class<?> createProxyClass(Enhancer enhancer) {
      enhancer.setInterceptDuringConstruction(false);
      return enhancer.createClass();
   }

   protected Object createProxyClassAndInstance(Enhancer enhancer, Callback[] callbacks) {
      enhancer.setInterceptDuringConstruction(false);
      enhancer.setCallbacks(callbacks);
      return this.constructorArgs != null && this.constructorArgTypes != null
         ? enhancer.create(this.constructorArgTypes, this.constructorArgs)
         : enhancer.create();
   }

   protected Enhancer createEnhancer() {
      return new Enhancer();
   }

   private void validateClassIfNecessary(Class<?> proxySuperClass, @Nullable ClassLoader proxyClassLoader) {
      if (!this.advised.isOptimize() && logger.isInfoEnabled()) {
         synchronized (validatedClasses) {
            validatedClasses.computeIfAbsent(proxySuperClass, clazz -> {
               this.doValidateClass((Class<?>)clazz, proxyClassLoader, ClassUtils.getAllInterfacesForClassAsSet(clazz));
               return Boolean.TRUE;
            });
         }
      }
   }

   private void doValidateClass(Class<?> proxySuperClass, @Nullable ClassLoader proxyClassLoader, Set<Class<?>> ifcs) {
      if (proxySuperClass != Object.class) {
         Method[] methods = proxySuperClass.getDeclaredMethods();

         for (Method method : methods) {
            int mod = method.getModifiers();
            if (!Modifier.isStatic(mod) && !Modifier.isPrivate(mod)) {
               if (Modifier.isFinal(mod)) {
                  if (logger.isWarnEnabled() && Modifier.isPublic(mod)) {
                     if (implementsInterface(method, ifcs)) {
                        if (!implementsOnlyConfigurationCallbackInterfaces(method, ifcs)) {
                           logger.warn(
                              "Unable to proxy interface-implementing method ["
                                 + method
                                 + "] because it is marked as final, consider using interface-based JDK proxies instead."
                           );
                        }
                     } else {
                        logger.warn(
                           "Public final method ["
                              + method
                              + "] cannot get proxied via CGLIB, consider removing the final marker or using interface-based JDK proxies."
                        );
                     }
                  }

                  if (logger.isDebugEnabled()) {
                     logger.debug(
                        "Final method ["
                           + method
                           + "] cannot get proxied via CGLIB: Calls to this method will NOT be routed to the target instance and might lead to NPEs against uninitialized fields in the proxy instance."
                     );
                  }
               } else if (logger.isDebugEnabled()
                  && !Modifier.isPublic(mod)
                  && !Modifier.isProtected(mod)
                  && proxyClassLoader != null
                  && proxySuperClass.getClassLoader() != proxyClassLoader) {
                  logger.debug(
                     "Method ["
                        + method
                        + "] is package-visible across different ClassLoaders and cannot get proxied via CGLIB: Declare this method as public or protected if you need to support invocations through the proxy."
                  );
               }
            }
         }

         this.doValidateClass(proxySuperClass.getSuperclass(), proxyClassLoader, ifcs);
      }
   }

   private Callback[] getCallbacks(Class<?> rootClass) throws Exception {
      boolean isStatic = this.advised.getTargetSource().isStatic();
      boolean isFrozen = this.advised.isFrozen();
      boolean exposeProxy = this.advised.isExposeProxy();
      Callback aopInterceptor = new CglibAopProxy.DynamicAdvisedInterceptor(this.advised);
      Callback targetInterceptor;
      if (exposeProxy) {
         targetInterceptor = isStatic
            ? new CglibAopProxy.StaticUnadvisedExposedInterceptor(this.advised.getTargetSource().getTarget())
            : new CglibAopProxy.DynamicUnadvisedExposedInterceptor(this.advised.getTargetSource());
      } else {
         targetInterceptor = isStatic
            ? new CglibAopProxy.StaticUnadvisedInterceptor(this.advised.getTargetSource().getTarget())
            : new CglibAopProxy.DynamicUnadvisedInterceptor(this.advised.getTargetSource());
      }

      Callback targetDispatcher = isStatic
         ? new CglibAopProxy.StaticDispatcher(this.advised.getTargetSource().getTarget())
         : new CglibAopProxy.SerializableNoOp();
      Callback[] mainCallbacks = new Callback[]{
         aopInterceptor,
         targetInterceptor,
         new CglibAopProxy.SerializableNoOp(),
         targetDispatcher,
         this.advisedDispatcher,
         new CglibAopProxy.EqualsInterceptor(this.advised),
         new CglibAopProxy.HashCodeInterceptor(this.advised)
      };
      if (isStatic && isFrozen) {
         Method[] methods = rootClass.getMethods();
         int methodsCount = methods.length;
         List<Callback> fixedCallbacks = new ArrayList<>(methodsCount);
         this.fixedInterceptorMap = CollectionUtils.newHashMap(methodsCount);
         int advicedMethodCount = methodsCount;

         for (int x = 0; x < methodsCount; x++) {
            Method method = methods[x];
            if (method.getDeclaringClass() == Object.class) {
               advicedMethodCount--;
            } else {
               List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, rootClass);
               fixedCallbacks.add(
                  new CglibAopProxy.FixedChainStaticTargetInterceptor(chain, this.advised.getTargetSource().getTarget(), this.advised.getTargetClass())
               );
               this.fixedInterceptorMap.put(method, x - (methodsCount - advicedMethodCount));
            }
         }

         Callback[] callbacks = new Callback[mainCallbacks.length + advicedMethodCount];
         System.arraycopy(mainCallbacks, 0, callbacks, 0, mainCallbacks.length);
         System.arraycopy(fixedCallbacks.toArray(Callback[]::new), 0, callbacks, mainCallbacks.length, advicedMethodCount);
         this.fixedInterceptorOffset = mainCallbacks.length;
         return callbacks;
      } else {
         return mainCallbacks;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof CglibAopProxy that && AopProxyUtils.equalsInProxy(this.advised, that.advised);
   }

   @Override
   public int hashCode() {
      return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
   }

   private static boolean implementsInterface(Method method, Set<Class<?>> ifcs) {
      for (Class<?> ifc : ifcs) {
         if (ClassUtils.hasMethod(ifc, method)) {
            return true;
         }
      }

      return false;
   }

   static boolean implementsOnlyConfigurationCallbackInterfaces(Method method, Set<Class<?>> ifcs) {
      boolean matched = false;

      for (Class<?> ifc : ifcs) {
         if (ClassUtils.hasMethod(ifc, method)) {
            if (!AopProxyUtils.isConfigurationCallbackInterface(ifc)) {
               return false;
            }

            matched = true;
         }
      }

      return matched;
   }

   private static @Nullable Object processReturnType(Object proxy, @Nullable Object target, Method method, Object[] arguments, @Nullable Object returnValue) {
      if (returnValue != null && returnValue == target && !RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
         returnValue = proxy;
      }

      Class<?> returnType = method.getReturnType();
      if (returnValue == null && returnType != void.class && returnType.isPrimitive()) {
         throw new AopInvocationException("Null return value from advice does not match primitive return type for: " + method);
      } else if (COROUTINES_REACTOR_PRESENT && KotlinDetector.isSuspendingFunction(method)) {
         return "kotlinx.coroutines.flow.Flow".equals(new MethodParameter(method, -1).getParameterType().getName())
            ? CoroutinesUtils.asFlow(returnValue)
            : CoroutinesUtils.awaitSingleOrNull(returnValue, arguments[arguments.length - 1]);
      } else {
         return returnValue;
      }
   }

   private static class AdvisedDispatcher implements Dispatcher, Serializable {
      private final AdvisedSupport advised;

      public AdvisedDispatcher(AdvisedSupport advised) {
         this.advised = advised;
      }

      public Object loadObject() {
         return this.advised;
      }
   }

   private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {
      private final AdvisedSupport advised;

      public DynamicAdvisedInterceptor(AdvisedSupport advised) {
         this.advised = advised;
      }

      public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
         Object oldProxy = null;
         boolean setProxyContext = false;
         Object target = null;
         TargetSource targetSource = this.advised.getTargetSource();

         try {
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
               retVal = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain).proceed();
            }

            return CglibAopProxy.processReturnType(proxy, target, method, args, retVal);
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
         return this == other
            || other instanceof CglibAopProxy.DynamicAdvisedInterceptor dynamicAdvisedInterceptor && this.advised.equals(dynamicAdvisedInterceptor.advised);
      }

      @Override
      public int hashCode() {
         return this.advised.hashCode();
      }
   }

   private static class DynamicUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {
      private final TargetSource targetSource;

      public DynamicUnadvisedExposedInterceptor(TargetSource targetSource) {
         this.targetSource = targetSource;
      }

      public @Nullable Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
         Object oldProxy = null;
         Object target = this.targetSource.getTarget();

         try {
            oldProxy = AopContext.setCurrentProxy(proxy);
            Object retVal = AopUtils.invokeJoinpointUsingReflection(target, method, args);
            return CglibAopProxy.processReturnType(proxy, target, method, args, retVal);
         } finally {
            AopContext.setCurrentProxy(oldProxy);
            if (target != null) {
               this.targetSource.releaseTarget(target);
            }
         }
      }
   }

   private static class DynamicUnadvisedInterceptor implements MethodInterceptor, Serializable {
      private final TargetSource targetSource;

      public DynamicUnadvisedInterceptor(TargetSource targetSource) {
         this.targetSource = targetSource;
      }

      public @Nullable Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
         Object target = this.targetSource.getTarget();

         try {
            Object retVal = AopUtils.invokeJoinpointUsingReflection(target, method, args);
            return CglibAopProxy.processReturnType(proxy, target, method, args, retVal);
         } finally {
            if (target != null) {
               this.targetSource.releaseTarget(target);
            }
         }
      }
   }

   private static class EqualsInterceptor implements MethodInterceptor, Serializable {
      private final AdvisedSupport advised;

      public EqualsInterceptor(AdvisedSupport advised) {
         this.advised = advised;
      }

      public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
         Object other = args[0];
         if (proxy == other) {
            return true;
         } else {
            return !(other instanceof Factory factory)
               ? false
               : factory.getCallback(5) instanceof CglibAopProxy.EqualsInterceptor that && AopProxyUtils.equalsInProxy(this.advised, that.advised);
         }
      }
   }

   private static class FixedChainStaticTargetInterceptor implements MethodInterceptor, Serializable {
      private final List<Object> adviceChain;
      private final @Nullable Object target;
      private final @Nullable Class<?> targetClass;

      public FixedChainStaticTargetInterceptor(List<Object> adviceChain, @Nullable Object target, @Nullable Class<?> targetClass) {
         this.adviceChain = adviceChain;
         this.target = target;
         this.targetClass = targetClass;
      }

      public @Nullable Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
         MethodInvocation invocation = new ReflectiveMethodInvocation(proxy, this.target, method, args, this.targetClass, this.adviceChain);
         Object retVal = invocation.proceed();
         return CglibAopProxy.processReturnType(proxy, this.target, method, args, retVal);
      }
   }

   private static class HashCodeInterceptor implements MethodInterceptor, Serializable {
      private final AdvisedSupport advised;

      public HashCodeInterceptor(AdvisedSupport advised) {
         this.advised = advised;
      }

      public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
         return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
      }
   }

   private static class ProxyCallbackFilter implements CallbackFilter {
      final AdvisedSupport advised;
      private final Map<Method, Integer> fixedInterceptorMap;
      private final int fixedInterceptorOffset;

      public ProxyCallbackFilter(AdvisedSupport advised, Map<Method, Integer> fixedInterceptorMap, int fixedInterceptorOffset) {
         this.advised = advised;
         this.fixedInterceptorMap = fixedInterceptorMap;
         this.fixedInterceptorOffset = fixedInterceptorOffset;
      }

      public int accept(Method method) {
         if (AopUtils.isFinalizeMethod(method)) {
            CglibAopProxy.logger.trace("Found finalize() method - using NO_OVERRIDE");
            return 2;
         }

         if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() && method.getDeclaringClass().isAssignableFrom(Advised.class)) {
            if (CglibAopProxy.logger.isTraceEnabled()) {
               CglibAopProxy.logger.trace("Method is declared on Advised interface: " + method);
            }

            return 4;
         } else if (AopUtils.isEqualsMethod(method)) {
            if (CglibAopProxy.logger.isTraceEnabled()) {
               CglibAopProxy.logger.trace("Found 'equals' method: " + method);
            }

            return 5;
         } else if (AopUtils.isHashCodeMethod(method)) {
            if (CglibAopProxy.logger.isTraceEnabled()) {
               CglibAopProxy.logger.trace("Found 'hashCode' method: " + method);
            }

            return 6;
         } else {
            Class<?> targetClass = this.advised.getTargetClass();
            List<?> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
            boolean haveAdvice = !chain.isEmpty();
            boolean isStatic = this.advised.getTargetSource().isStatic();
            boolean isFrozen = this.advised.isFrozen();
            boolean exposeProxy = this.advised.isExposeProxy();
            if (!haveAdvice && isFrozen) {
               if (!exposeProxy && isStatic) {
                  Class<?> returnType = method.getReturnType();
                  if (targetClass != null && returnType.isAssignableFrom(targetClass)) {
                     if (CglibAopProxy.logger.isTraceEnabled()) {
                        CglibAopProxy.logger
                           .trace("Method return type is assignable from target type and may therefore return 'this' - using INVOKE_TARGET: " + method);
                     }

                     return 1;
                  } else {
                     if (CglibAopProxy.logger.isTraceEnabled()) {
                        CglibAopProxy.logger.trace("Method return type ensures 'this' cannot be returned - using DISPATCH_TARGET: " + method);
                     }

                     return 3;
                  }
               } else {
                  return 1;
               }
            } else if (exposeProxy) {
               if (CglibAopProxy.logger.isTraceEnabled()) {
                  CglibAopProxy.logger.trace("Must expose proxy on advised method: " + method);
               }

               return 0;
            } else if (isStatic && isFrozen && this.fixedInterceptorMap.containsKey(method)) {
               if (CglibAopProxy.logger.isTraceEnabled()) {
                  CglibAopProxy.logger.trace("Method has advice and optimizations are enabled: " + method);
               }

               int index = this.fixedInterceptorMap.get(method);
               return index + this.fixedInterceptorOffset;
            } else {
               if (CglibAopProxy.logger.isTraceEnabled()) {
                  CglibAopProxy.logger.trace("Unable to apply any optimizations to advised method: " + method);
               }

               return 0;
            }
         }
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof CglibAopProxy.ProxyCallbackFilter that
               && this.advised.getAdvisorKey().equals(that.advised.getAdvisorKey())
               && AopProxyUtils.equalsProxiedInterfaces(this.advised, that.advised)
               && ObjectUtils.nullSafeEquals(this.advised.getTargetClass(), that.advised.getTargetClass())
               && this.advised.getTargetSource().isStatic() == that.advised.getTargetSource().isStatic()
               && this.advised.isFrozen() == that.advised.isFrozen()
               && this.advised.isExposeProxy() == that.advised.isExposeProxy()
               && this.advised.isOpaque() == that.advised.isOpaque();
      }

      @Override
      public int hashCode() {
         return this.advised.getAdvisorKey().hashCode();
      }
   }

   public static class SerializableNoOp implements NoOp, Serializable {
   }

   private static class StaticDispatcher implements Dispatcher, Serializable {
      private final @Nullable Object target;

      public StaticDispatcher(@Nullable Object target) {
         this.target = target;
      }

      public @Nullable Object loadObject() {
         return this.target;
      }
   }

   private static class StaticUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {
      private final @Nullable Object target;

      public StaticUnadvisedExposedInterceptor(@Nullable Object target) {
         this.target = target;
      }

      public @Nullable Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
         Object oldProxy = null;

         try {
            oldProxy = AopContext.setCurrentProxy(proxy);
            Object retVal = AopUtils.invokeJoinpointUsingReflection(this.target, method, args);
            return CglibAopProxy.processReturnType(proxy, this.target, method, args, retVal);
         } finally {
            AopContext.setCurrentProxy(oldProxy);
         }
      }
   }

   private static class StaticUnadvisedInterceptor implements MethodInterceptor, Serializable {
      private final @Nullable Object target;

      public StaticUnadvisedInterceptor(@Nullable Object target) {
         this.target = target;
      }

      public @Nullable Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
         Object retVal = AopUtils.invokeJoinpointUsingReflection(this.target, method, args);
         return CglibAopProxy.processReturnType(proxy, this.target, method, args, retVal);
      }
   }
}
