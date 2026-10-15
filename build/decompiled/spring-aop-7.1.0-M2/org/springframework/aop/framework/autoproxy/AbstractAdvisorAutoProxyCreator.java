package org.springframework.aop.framework.autoproxy;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;

public abstract class AbstractAdvisorAutoProxyCreator extends AbstractAutoProxyCreator {
   private @Nullable BeanFactoryAdvisorRetrievalHelper advisorRetrievalHelper;

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      super.setBeanFactory(beanFactory);
      if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
         this.initBeanFactory(clbf);
      } else {
         throw new IllegalArgumentException("AdvisorAutoProxyCreator requires a ConfigurableListableBeanFactory: " + beanFactory);
      }
   }

   protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      this.advisorRetrievalHelper = new AbstractAdvisorAutoProxyCreator.BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
   }

   @Override
   protected Object @Nullable [] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
      List<Advisor> advisors = this.findEligibleAdvisors(beanClass, beanName);
      return advisors.isEmpty() ? DO_NOT_PROXY : advisors.toArray();
   }

   protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
      List<Advisor> candidateAdvisors = this.findCandidateAdvisors();
      List<Advisor> eligibleAdvisors = this.findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
      this.extendAdvisors(eligibleAdvisors);
      if (!eligibleAdvisors.isEmpty()) {
         try {
            eligibleAdvisors = this.sortAdvisors(eligibleAdvisors);
         } catch (BeanCreationException ex) {
            throw new AopConfigException(
               "Advisor sorting failed with unexpected bean creation, probably due to custom use of the Ordered interface. Consider using the @Order annotation instead.",
               ex
            );
         }
      }

      return eligibleAdvisors;
   }

   protected List<Advisor> findCandidateAdvisors() {
      Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
      return this.advisorRetrievalHelper.findAdvisorBeans();
   }

   protected List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {
      ProxyCreationContext.setCurrentProxiedBeanName(beanName);

      try {
         return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
      } finally {
         ProxyCreationContext.setCurrentProxiedBeanName(null);
      }
   }

   protected boolean isEligibleAdvisorBean(String beanName) {
      return true;
   }

   protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
      AnnotationAwareOrderComparator.sort(advisors);
      return advisors;
   }

   protected void extendAdvisors(List<Advisor> candidateAdvisors) {
   }

   @Override
   protected boolean advisorsPreFiltered() {
      return true;
   }

   private class BeanFactoryAdvisorRetrievalHelperAdapter extends BeanFactoryAdvisorRetrievalHelper {
      public BeanFactoryAdvisorRetrievalHelperAdapter(ConfigurableListableBeanFactory beanFactory) {
         super(beanFactory);
      }

      @Override
      protected boolean isEligibleBean(String beanName) {
         return AbstractAdvisorAutoProxyCreator.this.isEligibleAdvisorBean(beanName);
      }
   }
}
