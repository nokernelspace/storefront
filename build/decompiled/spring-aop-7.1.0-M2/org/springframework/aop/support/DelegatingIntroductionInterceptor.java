package org.springframework.aop.support;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.util.Assert;

public class DelegatingIntroductionInterceptor extends IntroductionInfoSupport implements IntroductionInterceptor {
   private @Nullable Object delegate;

   public DelegatingIntroductionInterceptor(Object delegate) {
      this.init(delegate);
   }

   protected DelegatingIntroductionInterceptor() {
      this.init(this);
   }

   private void init(Object delegate) {
      Assert.notNull(delegate, "Delegate must not be null");
      this.delegate = delegate;
      this.implementInterfacesOnObject(delegate);
      this.suppressInterface(IntroductionInterceptor.class);
      this.suppressInterface(DynamicIntroductionAdvice.class);
   }

   @Override
   public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
      if (this.isMethodOnIntroducedInterface(mi)) {
         Object retVal = AopUtils.invokeJoinpointUsingReflection(this.delegate, mi.getMethod(), mi.getArguments());
         if (retVal == this.delegate && mi instanceof ProxyMethodInvocation pmi) {
            Object proxy = pmi.getProxy();
            if (mi.getMethod().getReturnType().isInstance(proxy)) {
               retVal = proxy;
            }
         }

         return retVal;
      } else {
         return this.doProceed(mi);
      }
   }

   protected @Nullable Object doProceed(MethodInvocation mi) throws Throwable {
      return mi.proceed();
   }
}
