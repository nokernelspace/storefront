package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.ThrowsAdvice;

class ThrowsAdviceAdapter implements AdvisorAdapter, Serializable {
   @Override
   public boolean supportsAdvice(Advice advice) {
      return advice instanceof ThrowsAdvice;
   }

   @Override
   public MethodInterceptor getInterceptor(Advisor advisor) {
      return new ThrowsAdviceInterceptor(advisor.getAdvice());
   }
}
