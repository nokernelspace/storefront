package org.springframework.aop.aspectj.autoproxy;

import java.util.Comparator;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJAopUtils;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;

class AspectJPrecedenceComparator implements Comparator<Advisor> {
   private static final int HIGHER_PRECEDENCE = -1;
   private static final int SAME_PRECEDENCE = 0;
   private static final int LOWER_PRECEDENCE = 1;
   private final Comparator<? super Advisor> advisorComparator;

   public AspectJPrecedenceComparator() {
      this.advisorComparator = AnnotationAwareOrderComparator.INSTANCE;
   }

   public AspectJPrecedenceComparator(Comparator<? super Advisor> advisorComparator) {
      Assert.notNull(advisorComparator, "Advisor comparator must not be null");
      this.advisorComparator = advisorComparator;
   }

   public int compare(Advisor o1, Advisor o2) {
      int advisorPrecedence = this.advisorComparator.compare(o1, o2);
      if (advisorPrecedence == 0 && this.declaredInSameAspect(o1, o2)) {
         advisorPrecedence = this.comparePrecedenceWithinAspect(o1, o2);
      }

      return advisorPrecedence;
   }

   private int comparePrecedenceWithinAspect(Advisor advisor1, Advisor advisor2) {
      boolean oneOrOtherIsAfterAdvice = AspectJAopUtils.isAfterAdvice(advisor1) || AspectJAopUtils.isAfterAdvice(advisor2);
      int adviceDeclarationOrderDelta = this.getAspectDeclarationOrder(advisor1) - this.getAspectDeclarationOrder(advisor2);
      if (oneOrOtherIsAfterAdvice) {
         if (adviceDeclarationOrderDelta < 0) {
            return 1;
         } else {
            return adviceDeclarationOrderDelta == 0 ? 0 : -1;
         }
      } else if (adviceDeclarationOrderDelta < 0) {
         return -1;
      } else {
         return adviceDeclarationOrderDelta == 0 ? 0 : 1;
      }
   }

   private boolean declaredInSameAspect(Advisor advisor1, Advisor advisor2) {
      return this.hasAspectName(advisor1) && this.hasAspectName(advisor2) && this.getAspectName(advisor1).equals(this.getAspectName(advisor2));
   }

   private boolean hasAspectName(Advisor advisor) {
      return advisor instanceof AspectJPrecedenceInformation || advisor.getAdvice() instanceof AspectJPrecedenceInformation;
   }

   private String getAspectName(Advisor advisor) {
      AspectJPrecedenceInformation precedenceInfo = AspectJAopUtils.getAspectJPrecedenceInformationFor(advisor);
      Assert.state(precedenceInfo != null, () -> "Unresolvable AspectJPrecedenceInformation for " + advisor);
      return precedenceInfo.getAspectName();
   }

   private int getAspectDeclarationOrder(Advisor advisor) {
      AspectJPrecedenceInformation precedenceInfo = AspectJAopUtils.getAspectJPrecedenceInformationFor(advisor);
      return precedenceInfo != null ? precedenceInfo.getDeclarationOrder() : 0;
   }
}
