package org.springframework.aop.aspectj;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.weaver.tools.JoinPointMatch;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ProxyMethodInvocation;

public class AspectJAroundAdvice extends AbstractAspectJAdvice implements MethodInterceptor, Serializable {
   public AspectJAroundAdvice(Method aspectJAroundAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {
      super(aspectJAroundAdviceMethod, pointcut, aif);
   }

   @Override
   public boolean isBeforeAdvice() {
      return false;
   }

   @Override
   public boolean isAfterAdvice() {
      return false;
   }

   @Override
   protected boolean supportsProceedingJoinPoint() {
      return true;
   }

   @Override
   public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
      if (mi instanceof ProxyMethodInvocation pmi) {
         ProceedingJoinPoint pjp = this.lazyGetProceedingJoinPoint(pmi);
         JoinPointMatch jpm = this.getJoinPointMatch(pmi);
         return this.invokeAdviceMethod(pjp, jpm, null, null);
      } else {
         throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
      }
   }

   protected ProceedingJoinPoint lazyGetProceedingJoinPoint(ProxyMethodInvocation rmi) {
      return new MethodInvocationProceedingJoinPoint(rmi);
   }
}
