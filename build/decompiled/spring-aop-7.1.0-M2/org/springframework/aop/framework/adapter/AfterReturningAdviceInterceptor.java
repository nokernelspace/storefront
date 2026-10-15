package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.AfterAdvice;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.util.Assert;

public class AfterReturningAdviceInterceptor implements MethodInterceptor, AfterAdvice, Serializable {
   private final AfterReturningAdvice advice;

   public AfterReturningAdviceInterceptor(AfterReturningAdvice advice) {
      Assert.notNull(advice, "Advice must not be null");
      this.advice = advice;
   }

   @Override
   public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
      Object retVal = mi.proceed();
      this.advice.afterReturning(retVal, mi.getMethod(), mi.getArguments(), mi.getThis());
      return retVal;
   }
}
