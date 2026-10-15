package org.springframework.aop.aspectj;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.AfterAdvice;

public class AspectJAfterThrowingAdvice extends AbstractAspectJAdvice implements MethodInterceptor, AfterAdvice, Serializable {
   public AspectJAfterThrowingAdvice(Method aspectJBeforeAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {
      super(aspectJBeforeAdviceMethod, pointcut, aif);
   }

   @Override
   public boolean isBeforeAdvice() {
      return false;
   }

   @Override
   public boolean isAfterAdvice() {
      return true;
   }

   @Override
   public void setThrowingName(String name) {
      this.setThrowingNameNoCheck(name);
   }

   @Override
   public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
      try {
         return mi.proceed();
      } catch (Throwable ex) {
         if (this.shouldInvokeOnThrowing(ex)) {
            this.invokeAdviceMethod(this.getJoinPointMatch(), null, ex);
         }

         throw ex;
      }
   }

   private boolean shouldInvokeOnThrowing(Throwable ex) {
      return this.getDiscoveredThrowingType().isInstance(ex);
   }
}
