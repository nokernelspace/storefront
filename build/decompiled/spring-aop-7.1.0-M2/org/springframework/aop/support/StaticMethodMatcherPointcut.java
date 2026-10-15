package org.springframework.aop.support;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

public abstract class StaticMethodMatcherPointcut extends StaticMethodMatcher implements Pointcut {
   private ClassFilter classFilter = ClassFilter.TRUE;

   public void setClassFilter(ClassFilter classFilter) {
      this.classFilter = classFilter;
   }

   @Override
   public ClassFilter getClassFilter() {
      return this.classFilter;
   }

   @Override
   public final MethodMatcher getMethodMatcher() {
      return this;
   }
}
