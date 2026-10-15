package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.Assert;

public class AnnotationAwareAspectJAutoProxyCreator extends AspectJAwareAdvisorAutoProxyCreator {
   private @Nullable List<Pattern> includePatterns;
   private @Nullable AspectJAdvisorFactory aspectJAdvisorFactory;
   private @Nullable BeanFactoryAspectJAdvisorsBuilder aspectJAdvisorsBuilder;

   public void setIncludePatterns(List<String> patterns) {
      this.includePatterns = new ArrayList<>(patterns.size());

      for (String patternText : patterns) {
         this.includePatterns.add(Pattern.compile(patternText));
      }
   }

   public void setAspectJAdvisorFactory(AspectJAdvisorFactory aspectJAdvisorFactory) {
      Assert.notNull(aspectJAdvisorFactory, "AspectJAdvisorFactory must not be null");
      this.aspectJAdvisorFactory = aspectJAdvisorFactory;
   }

   @Override
   protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      super.initBeanFactory(beanFactory);
      if (this.aspectJAdvisorFactory == null) {
         this.aspectJAdvisorFactory = new ReflectiveAspectJAdvisorFactory(beanFactory);
      }

      this.aspectJAdvisorsBuilder = new AnnotationAwareAspectJAutoProxyCreator.BeanFactoryAspectJAdvisorsBuilderAdapter(beanFactory, this.aspectJAdvisorFactory);
   }

   @Override
   protected List<Advisor> findCandidateAdvisors() {
      List<Advisor> advisors = super.findCandidateAdvisors();
      if (this.aspectJAdvisorsBuilder != null) {
         advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
      }

      return advisors;
   }

   @Override
   protected boolean isInfrastructureClass(Class<?> beanClass) {
      return super.isInfrastructureClass(beanClass) || this.aspectJAdvisorFactory != null && this.aspectJAdvisorFactory.isAspect(beanClass);
   }

   protected boolean isEligibleAspectBean(String beanName) {
      if (this.includePatterns == null) {
         return true;
      }

      for (Pattern pattern : this.includePatterns) {
         if (pattern.matcher(beanName).matches()) {
            return true;
         }
      }

      return false;
   }

   private class BeanFactoryAspectJAdvisorsBuilderAdapter extends BeanFactoryAspectJAdvisorsBuilder {
      public BeanFactoryAspectJAdvisorsBuilderAdapter(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
         super(beanFactory, advisorFactory);
      }

      @Override
      protected boolean isEligibleBean(String beanName) {
         return AnnotationAwareAspectJAutoProxyCreator.this.isEligibleAspectBean(beanName);
      }
   }
}
