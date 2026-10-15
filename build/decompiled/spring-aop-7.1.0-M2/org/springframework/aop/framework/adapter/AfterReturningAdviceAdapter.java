package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.AfterReturningAdvice;

class AfterReturningAdviceAdapter implements AdvisorAdapter, Serializable {
   @Override
   public boolean supportsAdvice(Advice advice) {
      return advice instanceof AfterReturningAdvice;
   }

   @Override
   public MethodInterceptor getInterceptor(Advisor advisor) {
      AfterReturningAdvice advice = (AfterReturningAdvice)advisor.getAdvice();
      return new AfterReturningAdviceInterceptor(advice);
   }
}
