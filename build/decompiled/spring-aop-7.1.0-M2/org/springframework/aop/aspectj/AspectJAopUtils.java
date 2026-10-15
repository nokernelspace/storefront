package org.springframework.aop.aspectj;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.AfterAdvice;
import org.springframework.aop.BeforeAdvice;

public abstract class AspectJAopUtils {
   public static boolean isBeforeAdvice(Advisor anAdvisor) {
      AspectJPrecedenceInformation precedenceInfo = getAspectJPrecedenceInformationFor(anAdvisor);
      return precedenceInfo != null ? precedenceInfo.isBeforeAdvice() : anAdvisor.getAdvice() instanceof BeforeAdvice;
   }

   public static boolean isAfterAdvice(Advisor anAdvisor) {
      AspectJPrecedenceInformation precedenceInfo = getAspectJPrecedenceInformationFor(anAdvisor);
      return precedenceInfo != null ? precedenceInfo.isAfterAdvice() : anAdvisor.getAdvice() instanceof AfterAdvice;
   }

   public static @Nullable AspectJPrecedenceInformation getAspectJPrecedenceInformationFor(Advisor anAdvisor) {
      if (anAdvisor instanceof AspectJPrecedenceInformation ajpi) {
         return ajpi;
      } else {
         return anAdvisor.getAdvice() instanceof AspectJPrecedenceInformation ajpi ? ajpi : null;
      }
   }
}
