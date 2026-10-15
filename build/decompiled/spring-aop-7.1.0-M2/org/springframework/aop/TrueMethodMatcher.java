package org.springframework.aop;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

final class TrueMethodMatcher implements MethodMatcher, Serializable {
   public static final TrueMethodMatcher INSTANCE = new TrueMethodMatcher();

   private TrueMethodMatcher() {
   }

   @Override
   public boolean isRuntime() {
      return false;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass) {
      return true;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
      throw new UnsupportedOperationException();
   }

   @Override
   public String toString() {
      return "MethodMatcher.TRUE";
   }

   private Object readResolve() {
      return INSTANCE;
   }
}
