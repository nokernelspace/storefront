package org.springframework.context.aot;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.KotlinDetector;

class KotlinReflectionBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {
   public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
      Class<?> beanClass = registeredBean.getBeanClass();
      return KotlinDetector.isKotlinType(beanClass) ? new KotlinReflectionBeanRegistrationAotProcessor.AotContribution(beanClass) : null;
   }

   private static class AotContribution implements BeanRegistrationAotContribution {
      private final Class<?> beanClass;

      public AotContribution(Class<?> beanClass) {
         this.beanClass = beanClass;
      }

      public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
         this.registerHints(this.beanClass, generationContext.getRuntimeHints());
      }

      private void registerHints(Class<?> type, RuntimeHints runtimeHints) {
         if (KotlinDetector.isKotlinType(type)) {
            runtimeHints.reflection().registerType(type, new MemberCategory[0]);
         }

         Class<?> superClass = type.getSuperclass();
         if (superClass != null) {
            this.registerHints(superClass, runtimeHints);
         }

         Class<?> enclosingClass = type.getEnclosingClass();
         if (enclosingClass != null) {
            runtimeHints.reflection().registerType(enclosingClass, new MemberCategory[0]);
         }
      }
   }
}
