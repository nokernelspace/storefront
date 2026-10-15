package org.springframework.aop.support;

import java.util.Map;
import java.util.WeakHashMap;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.util.ReflectionUtils;

public class DelegatePerTargetObjectIntroductionInterceptor extends IntroductionInfoSupport implements IntroductionInterceptor {
   private final Map<Object, Object> delegateMap = new WeakHashMap<>();
   private final Class<?> defaultImplType;
   private final Class<?> interfaceType;

   public DelegatePerTargetObjectIntroductionInterceptor(Class<?> defaultImplType, Class<?> interfaceType) {
      this.defaultImplType = defaultImplType;
      this.interfaceType = interfaceType;
      Object delegate = this.createNewDelegate();
      this.implementInterfacesOnObject(delegate);
      this.suppressInterface(IntroductionInterceptor.class);
      this.suppressInterface(DynamicIntroductionAdvice.class);
   }

   @Override
   public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
      if (this.isMethodOnIntroducedInterface(mi)) {
         Object delegate = this.getIntroductionDelegateFor(mi.getThis());
         Object retVal = AopUtils.invokeJoinpointUsingReflection(delegate, mi.getMethod(), mi.getArguments());
         if (retVal == delegate && mi instanceof ProxyMethodInvocation pmi) {
            retVal = pmi.getProxy();
         }

         return retVal;
      } else {
         return this.doProceed(mi);
      }
   }

   protected @Nullable Object doProceed(MethodInvocation mi) throws Throwable {
      return mi.proceed();
   }

   private Object getIntroductionDelegateFor(@Nullable Object targetObject) {
      synchronized (this.delegateMap) {
         if (this.delegateMap.containsKey(targetObject)) {
            return this.delegateMap.get(targetObject);
         }

         Object delegate = this.createNewDelegate();
         this.delegateMap.put(targetObject, delegate);
         return delegate;
      }
   }

   private Object createNewDelegate() {
      try {
         return ReflectionUtils.accessibleConstructor(this.defaultImplType, new Class[0]).newInstance();
      } catch (Throwable ex) {
         throw new IllegalArgumentException(
            "Cannot create default implementation for '" + this.interfaceType.getName() + "' mixin (" + this.defaultImplType.getName() + "): " + ex
         );
      }
   }
}
