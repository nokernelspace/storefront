package org.springframework.aop.support;

import java.lang.reflect.Method;
import org.springframework.aop.MethodMatcher;

public abstract class DynamicMethodMatcher implements MethodMatcher {
   @Override
   public final boolean isRuntime() {
      return true;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass) {
      return true;
   }
}
