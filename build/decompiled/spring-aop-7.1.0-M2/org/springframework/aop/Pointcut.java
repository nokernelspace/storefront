package org.springframework.aop;

public interface Pointcut {
   Pointcut TRUE = TruePointcut.INSTANCE;

   ClassFilter getClassFilter();

   MethodMatcher getMethodMatcher();
}
