package org.springframework.aop.aspectj;

import org.aopalliance.aop.Advice;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.support.ClassFilters;
import org.springframework.aop.support.DelegatePerTargetObjectIntroductionInterceptor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;

public class DeclareParentsAdvisor implements IntroductionAdvisor {
   private final Advice advice;
   private final Class<?> introducedInterface;
   private final ClassFilter typePatternClassFilter;

   public DeclareParentsAdvisor(Class<?> interfaceType, String typePattern, Class<?> defaultImpl) {
      this(interfaceType, typePattern, new DelegatePerTargetObjectIntroductionInterceptor(defaultImpl, interfaceType));
   }

   public DeclareParentsAdvisor(Class<?> interfaceType, String typePattern, Object delegateRef) {
      this(interfaceType, typePattern, new DelegatingIntroductionInterceptor(delegateRef));
   }

   private DeclareParentsAdvisor(Class<?> interfaceType, String typePattern, IntroductionInterceptor interceptor) {
      this.advice = interceptor;
      this.introducedInterface = interfaceType;
      ClassFilter typePatternFilter = new TypePatternClassFilter(typePattern);
      ClassFilter exclusion = clazz -> !this.introducedInterface.isAssignableFrom(clazz);
      this.typePatternClassFilter = ClassFilters.intersection(typePatternFilter, exclusion);
   }

   @Override
   public ClassFilter getClassFilter() {
      return this.typePatternClassFilter;
   }

   @Override
   public void validateInterfaces() throws IllegalArgumentException {
   }

   @Override
   public Advice getAdvice() {
      return this.advice;
   }

   @Override
   public Class<?>[] getInterfaces() {
      return new Class[]{this.introducedInterface};
   }
}
