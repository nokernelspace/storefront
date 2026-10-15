package org.springframework.aop.aspectj.autoproxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.aspectj.util.PartialOrder;
import org.aspectj.util.PartialOrder.PartialComparable;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJPointcutAdvisor;
import org.springframework.aop.aspectj.AspectJProxyUtils;
import org.springframework.aop.aspectj.ShadowMatchUtils;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

public class AspectJAwareAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator implements SmartInitializingSingleton, DisposableBean {
   private static final Comparator<Advisor> DEFAULT_PRECEDENCE_COMPARATOR = new AspectJPrecedenceComparator();

   @Override
   protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
      List<AspectJAwareAdvisorAutoProxyCreator.PartiallyComparableAdvisorHolder> partiallyComparableAdvisors = new ArrayList<>(advisors.size());

      for (Advisor advisor : advisors) {
         partiallyComparableAdvisors.add(new AspectJAwareAdvisorAutoProxyCreator.PartiallyComparableAdvisorHolder(advisor, DEFAULT_PRECEDENCE_COMPARATOR));
      }

      List<AspectJAwareAdvisorAutoProxyCreator.PartiallyComparableAdvisorHolder> sorted = PartialOrder.sort(partiallyComparableAdvisors);
      if (sorted == null) {
         return super.sortAdvisors(advisors);
      }

      List<Advisor> result = new ArrayList<>(advisors.size());

      for (AspectJAwareAdvisorAutoProxyCreator.PartiallyComparableAdvisorHolder pcAdvisor : sorted) {
         result.add(pcAdvisor.getAdvisor());
      }

      return result;
   }

   @Override
   protected void extendAdvisors(List<Advisor> candidateAdvisors) {
      AspectJProxyUtils.makeAdvisorChainAspectJCapableIfNecessary(candidateAdvisors);
   }

   @Override
   protected boolean shouldSkip(Class<?> beanClass, String beanName) {
      for (Advisor advisor : this.findCandidateAdvisors()) {
         if (advisor instanceof AspectJPointcutAdvisor pointcutAdvisor && pointcutAdvisor.getAspectName().equals(beanName)) {
            return true;
         }
      }

      return super.shouldSkip(beanClass, beanName);
   }

   public void afterSingletonsInstantiated() {
      ShadowMatchUtils.clearCache();
   }

   public void destroy() {
      ShadowMatchUtils.clearCache();
   }

   private static class PartiallyComparableAdvisorHolder implements PartialComparable {
      private final Advisor advisor;
      private final Comparator<Advisor> comparator;

      public PartiallyComparableAdvisorHolder(Advisor advisor, Comparator<Advisor> comparator) {
         this.advisor = advisor;
         this.comparator = comparator;
      }

      public int compareTo(Object obj) {
         Advisor otherAdvisor = ((AspectJAwareAdvisorAutoProxyCreator.PartiallyComparableAdvisorHolder)obj).advisor;
         return this.comparator.compare(this.advisor, otherAdvisor);
      }

      public int fallbackCompareTo(Object obj) {
         return 0;
      }

      public Advisor getAdvisor() {
         return this.advisor;
      }

      @Override
      public String toString() {
         Advice advice = this.advisor.getAdvice();
         StringBuilder sb = new StringBuilder(ClassUtils.getShortName(advice.getClass()));
         boolean appended = false;
         if (this.advisor instanceof Ordered ordered) {
            sb.append(": order = ").append(ordered.getOrder());
            appended = true;
         }

         if (advice instanceof AbstractAspectJAdvice ajAdvice) {
            sb.append(!appended ? ": " : ", ");
            sb.append("aspect name = ");
            sb.append(ajAdvice.getAspectName());
            sb.append(", declaration order = ");
            sb.append(ajAdvice.getDeclarationOrder());
         }

         return sb.toString();
      }
   }
}
