package org.springframework.aop.aspectj;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.lang.Contract;
import org.springframework.util.StringUtils;

public abstract class AspectJProxyUtils {
   public static boolean makeAdvisorChainAspectJCapableIfNecessary(List<Advisor> advisors) {
      if (!advisors.isEmpty()) {
         boolean foundAspectJAdvice = false;

         for (Advisor advisor : advisors) {
            if (isAspectJAdvice(advisor)) {
               foundAspectJAdvice = true;
               break;
            }
         }

         if (foundAspectJAdvice && !advisors.contains(ExposeInvocationInterceptor.ADVISOR)) {
            advisors.add(0, ExposeInvocationInterceptor.ADVISOR);
            return true;
         }
      }

      return false;
   }

   private static boolean isAspectJAdvice(Advisor advisor) {
      return advisor instanceof InstantiationModelAwarePointcutAdvisor
         || advisor.getAdvice() instanceof AbstractAspectJAdvice
         || advisor instanceof PointcutAdvisor pointcutAdvisor && pointcutAdvisor.getPointcut() instanceof AspectJExpressionPointcut;
   }

   @Contract("null -> false")
   static boolean isVariableName(@Nullable String name) {
      if (!StringUtils.hasLength(name)) {
         return false;
      }

      if (!Character.isJavaIdentifierStart(name.charAt(0))) {
         return false;
      }

      for (int i = 1; i < name.length(); i++) {
         if (!Character.isJavaIdentifierPart(name.charAt(i))) {
            return false;
         }
      }

      return true;
   }
}
