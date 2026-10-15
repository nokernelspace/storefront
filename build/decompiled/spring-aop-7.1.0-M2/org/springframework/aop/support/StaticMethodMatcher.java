package org.springframework.aop.support;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.MethodMatcher;

public abstract class StaticMethodMatcher implements MethodMatcher {
   @Override
   public final boolean isRuntime() {
      return false;
   }

   @Override
   public final boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
      throw new UnsupportedOperationException("Illegal MethodMatcher usage");
   }
}
