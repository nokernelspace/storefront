package org.springframework.aop.aspectj.annotation;

import java.lang.reflect.Field;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.util.ClassUtils;

class AspectJAdvisorBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   private static final String AJC_MAGIC = "ajc$";
   private static final boolean ASPECTJ_PRESENT = ClassUtils.isPresent(
      "org.aspectj.lang.annotation.Pointcut", AspectJAdvisorBeanRegistrationAotProcessor.class.getClassLoader()
   );

   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      if (ASPECTJ_PRESENT) {
         Class<?> beanClass = registeredBean.getBeanClass();
         if (compiledByAjc(beanClass)) {
            return new AspectJAdvisorBeanRegistrationAotProcessor.AspectJAdvisorContribution(beanClass);
         }
      }

      return null;
   }

   private static boolean compiledByAjc(Class<?> clazz) {
      for (Field field : clazz.getDeclaredFields()) {
         if (field.getName().startsWith("ajc$")) {
            return true;
         }
      }

      return false;
   }

   private static class AspectJAdvisorContribution implements BeanRegistrationAotContribution {
      private final Class<?> beanClass;

      public AspectJAdvisorContribution(Class<?> beanClass) {
         this.beanClass = beanClass;
      }

      public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         generationContext.getRuntimeHints().reflection().registerType(this.beanClass, new MemberCategory[]{MemberCategory.ACCESS_DECLARED_FIELDS});
      }
   }
}
