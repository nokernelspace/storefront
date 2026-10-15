package org.springframework.aop.aspectj.annotation;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

class AspectJBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {
   private static final boolean ASPECTJ_PRESENT = ClassUtils.isPresent(
      "org.aspectj.lang.annotation.Pointcut", AspectJBeanFactoryInitializationAotProcessor.class.getClassLoader()
   );

   public @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
      return ASPECTJ_PRESENT ? AspectJBeanFactoryInitializationAotProcessor.AspectDelegate.processAheadOfTime(beanFactory) : null;
   }

   private static class AspectContribution implements BeanFactoryInitializationAotContribution {
      private final List<Advisor> advisors;

      public AspectContribution(List<Advisor> advisors) {
         this.advisors = advisors;
      }

      public void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode) {
         ReflectionHints reflectionHints = generationContext.getRuntimeHints().reflection();

         for (Advisor advisor : this.advisors) {
            if (advisor.getAdvice() instanceof AbstractAspectJAdvice aspectJAdvice) {
               reflectionHints.registerMethod(aspectJAdvice.getAspectJAdviceMethod(), ExecutableMode.INVOKE);
            }
         }
      }
   }

   private static class AspectDelegate {
      private static AspectJBeanFactoryInitializationAotProcessor.@Nullable AspectContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
         BeanFactoryAspectJAdvisorsBuilder builder = new BeanFactoryAspectJAdvisorsBuilder(beanFactory);
         List<Advisor> advisors = builder.buildAspectJAdvisors();
         return advisors.isEmpty() ? null : new AspectJBeanFactoryInitializationAotProcessor.AspectContribution(advisors);
      }
   }
}
