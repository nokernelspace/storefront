package org.springframework.aop.support;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

public abstract class DynamicMethodMatcherPointcut extends DynamicMethodMatcher implements Pointcut {
   @Override
   public ClassFilter getClassFilter() {
      return ClassFilter.TRUE;
   }

   @Override
   public final MethodMatcher getMethodMatcher() {
      return this;
   }
}
