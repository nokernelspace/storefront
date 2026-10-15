package org.springframework.aop.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.util.Assert;

public abstract class Pointcuts {
   public static final Pointcut SETTERS = Pointcuts.SetterPointcut.INSTANCE;
   public static final Pointcut GETTERS = Pointcuts.GetterPointcut.INSTANCE;

   public static Pointcut union(Pointcut pc1, Pointcut pc2) {
      return new ComposablePointcut(pc1).union(pc2);
   }

   public static Pointcut intersection(Pointcut pc1, Pointcut pc2) {
      return new ComposablePointcut(pc1).intersection(pc2);
   }

   public static boolean matches(Pointcut pointcut, Method method, Class<?> targetClass, Object... args) {
      Assert.notNull(pointcut, "Pointcut must not be null");
      if (pointcut == Pointcut.TRUE) {
         return true;
      }

      if (pointcut.getClassFilter().matches(targetClass)) {
         MethodMatcher mm = pointcut.getMethodMatcher();
         if (mm.matches(method, targetClass)) {
            return !mm.isRuntime() || mm.matches(method, targetClass, args);
         }
      }

      return false;
   }

   private static class GetterPointcut extends StaticMethodMatcherPointcut implements Serializable {
      public static final Pointcuts.GetterPointcut INSTANCE = new Pointcuts.GetterPointcut();

      @Override
      public boolean matches(Method method, Class<?> targetClass) {
         return method.getName().startsWith("get") && method.getParameterCount() == 0 && method.getReturnType() != void.class;
      }

      private Object readResolve() {
         return INSTANCE;
      }

      @Override
      public String toString() {
         return "Pointcuts.GETTERS";
      }
   }

   private static class SetterPointcut extends StaticMethodMatcherPointcut implements Serializable {
      public static final Pointcuts.SetterPointcut INSTANCE = new Pointcuts.SetterPointcut();

      @Override
      public boolean matches(Method method, Class<?> targetClass) {
         return method.getName().startsWith("set") && method.getParameterCount() == 1 && method.getReturnType() == void.class;
      }

      private Object readResolve() {
         return INSTANCE;
      }

      @Override
      public String toString() {
         return "Pointcuts.SETTERS";
      }
   }
}
