package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {
   private static final MethodInterceptor[] EMPTY_METHOD_INTERCEPTOR_ARRAY = new MethodInterceptor[0];
   private final List<AdvisorAdapter> adapters = new ArrayList<>(3);

   public DefaultAdvisorAdapterRegistry() {
      this.registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
      this.registerAdvisorAdapter(new AfterReturningAdviceAdapter());
      this.registerAdvisorAdapter(new ThrowsAdviceAdapter());
   }

   @Override
   public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
      if (adviceObject instanceof Advisor advisor) {
         return advisor;
      } else if (adviceObject instanceof Advice advice) {
         if (advice instanceof MethodInterceptor) {
            return new DefaultPointcutAdvisor(advice);
         }

         for (AdvisorAdapter adapter : this.adapters) {
            if (adapter.supportsAdvice(advice)) {
               return new DefaultPointcutAdvisor(advice);
            }
         }

         throw new UnknownAdviceTypeException(advice);
      } else {
         throw new UnknownAdviceTypeException(adviceObject);
      }
   }

   @Override
   public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
      List<MethodInterceptor> interceptors = new ArrayList<>(3);
      Advice advice = advisor.getAdvice();
      if (advice instanceof MethodInterceptor methodInterceptor) {
         interceptors.add(methodInterceptor);
      }

      for (AdvisorAdapter adapter : this.adapters) {
         if (adapter.supportsAdvice(advice)) {
            interceptors.add(adapter.getInterceptor(advisor));
         }
      }

      if (interceptors.isEmpty()) {
         throw new UnknownAdviceTypeException(advisor.getAdvice());
      } else {
         return interceptors.toArray(EMPTY_METHOD_INTERCEPTOR_ARRAY);
      }
   }

   @Override
   public void registerAdvisorAdapter(AdvisorAdapter adapter) {
      this.adapters.add(adapter);
   }
}
