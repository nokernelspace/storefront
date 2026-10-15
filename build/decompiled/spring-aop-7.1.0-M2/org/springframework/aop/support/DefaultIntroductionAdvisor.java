package org.springframework.aop.support;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInfo;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class DefaultIntroductionAdvisor implements IntroductionAdvisor, ClassFilter, Ordered, Serializable {
   private final Advice advice;
   private final Set<Class<?>> interfaces = new LinkedHashSet<>();
   private int order = Integer.MAX_VALUE;

   public DefaultIntroductionAdvisor(Advice advice) {
      this(advice, advice instanceof IntroductionInfo introductionInfo ? introductionInfo : null);
   }

   public DefaultIntroductionAdvisor(Advice advice, @Nullable IntroductionInfo introductionInfo) {
      Assert.notNull(advice, "Advice must not be null");
      this.advice = advice;
      if (introductionInfo != null) {
         Class<?>[] introducedInterfaces = introductionInfo.getInterfaces();
         if (introducedInterfaces.length == 0) {
            throw new IllegalArgumentException("IntroductionInfo defines no interfaces to introduce: " + introductionInfo);
         }

         for (Class<?> ifc : introducedInterfaces) {
            this.addInterface(ifc);
         }
      }
   }

   public DefaultIntroductionAdvisor(DynamicIntroductionAdvice advice, Class<?> ifc) {
      Assert.notNull(advice, "Advice must not be null");
      this.advice = advice;
      this.addInterface(ifc);
   }

   public void addInterface(Class<?> ifc) {
      Assert.notNull(ifc, "Interface must not be null");
      if (!ifc.isInterface()) {
         throw new IllegalArgumentException("Specified class [" + ifc.getName() + "] must be an interface");
      }

      this.interfaces.add(ifc);
   }

   @Override
   public Class<?>[] getInterfaces() {
      return ClassUtils.toClassArray(this.interfaces);
   }

   @Override
   public void validateInterfaces() throws IllegalArgumentException {
      for (Class<?> ifc : this.interfaces) {
         if (this.advice instanceof DynamicIntroductionAdvice dynamicIntroductionAdvice && !dynamicIntroductionAdvice.implementsInterface(ifc)) {
            throw new IllegalArgumentException(
               "DynamicIntroductionAdvice [" + this.advice + "] does not implement interface [" + ifc.getName() + "] specified for introduction"
            );
         }
      }
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public Advice getAdvice() {
      return this.advice;
   }

   @Override
   public ClassFilter getClassFilter() {
      return this;
   }

   @Override
   public boolean matches(Class<?> clazz) {
      return true;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof DefaultIntroductionAdvisor otherAdvisor
            && this.advice.equals(otherAdvisor.advice)
            && this.interfaces.equals(otherAdvisor.interfaces);
   }

   @Override
   public int hashCode() {
      return this.advice.hashCode() * 13 + this.interfaces.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": advice [" + this.advice + "]; interfaces " + ClassUtils.classNamesToString(this.interfaces);
   }
}
