package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;

public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {
   public static final DefaultAdvisorChainFactory INSTANCE = new DefaultAdvisorChainFactory();

   @Override
   public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass) {
      AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
      Advisor[] advisors = config.getAdvisors();
      List<Object> interceptorList = new ArrayList<>(advisors.length);
      Class<?> actualClass = targetClass != null ? targetClass : method.getDeclaringClass();
      Boolean hasIntroductions = null;

      for (Advisor advisor : advisors) {
         if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
            if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
               MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
               boolean match;
               if (mm instanceof IntroductionAwareMethodMatcher iamm) {
                  if (hasIntroductions == null) {
                     hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
                  }

                  match = iamm.matches(method, actualClass, hasIntroductions);
               } else {
                  match = mm.matches(method, actualClass);
               }

               if (match) {
                  MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
                  if (mm.isRuntime()) {
                     for (MethodInterceptor interceptor : interceptors) {
                        interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
                     }
                  } else {
                     interceptorList.addAll(Arrays.asList(interceptors));
                  }
               }
            }
         } else if (advisor instanceof IntroductionAdvisor ia) {
            if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
               Interceptor[] interceptors = registry.getInterceptors(advisor);
               interceptorList.addAll(Arrays.asList(interceptors));
            }
         } else {
            Interceptor[] interceptors = registry.getInterceptors(advisor);
            interceptorList.addAll(Arrays.asList(interceptors));
         }
      }

      return interceptorList;
   }

   private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
      for (Advisor advisor : advisors) {
         if (advisor instanceof IntroductionAdvisor ia && ia.getClassFilter().matches(actualClass)) {
            return true;
         }
      }

      return false;
   }
}
