package org.springframework.aop.framework.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;

public interface AdvisorAdapter {
   boolean supportsAdvice(Advice advice);

   MethodInterceptor getInterceptor(Advisor advisor);
}
