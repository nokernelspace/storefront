package org.springframework.aop.framework.adapter;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;

public interface AdvisorAdapterRegistry {
   Advisor wrap(Object advice) throws UnknownAdviceTypeException;

   MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException;

   void registerAdvisorAdapter(AdvisorAdapter adapter);
}
