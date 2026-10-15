package org.springframework.aop.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.util.Assert;

public abstract class MethodMatchers {
   public static MethodMatcher union(MethodMatcher mm1, MethodMatcher mm2) {
      return !(mm1 instanceof IntroductionAwareMethodMatcher) && !(mm2 instanceof IntroductionAwareMethodMatcher)
         ? new MethodMatchers.UnionMethodMatcher(mm1, mm2)
         : new MethodMatchers.UnionIntroductionAwareMethodMatcher(mm1, mm2);
   }

   static MethodMatcher union(MethodMatcher mm1, ClassFilter cf1, MethodMatcher mm2, ClassFilter cf2) {
      return !(mm1 instanceof IntroductionAwareMethodMatcher) && !(mm2 instanceof IntroductionAwareMethodMatcher)
         ? new MethodMatchers.ClassFilterAwareUnionMethodMatcher(mm1, cf1, mm2, cf2)
         : new MethodMatchers.ClassFilterAwareUnionIntroductionAwareMethodMatcher(mm1, cf1, mm2, cf2);
   }

   public static MethodMatcher intersection(MethodMatcher mm1, MethodMatcher mm2) {
      return !(mm1 instanceof IntroductionAwareMethodMatcher) && !(mm2 instanceof IntroductionAwareMethodMatcher)
         ? new MethodMatchers.IntersectionMethodMatcher(mm1, mm2)
         : new MethodMatchers.IntersectionIntroductionAwareMethodMatcher(mm1, mm2);
   }

   public static MethodMatcher negate(MethodMatcher methodMatcher) {
      Assert.notNull(methodMatcher, "MethodMatcher must not be null");
      return new MethodMatchers.NegateMethodMatcher(methodMatcher);
   }

   public static boolean matches(MethodMatcher mm, Method method, Class<?> targetClass, boolean hasIntroductions) {
      Assert.notNull(mm, "MethodMatcher must not be null");
      return mm instanceof IntroductionAwareMethodMatcher iamm ? iamm.matches(method, targetClass, hasIntroductions) : mm.matches(method, targetClass);
   }

   private static class ClassFilterAwareUnionIntroductionAwareMethodMatcher
      extends MethodMatchers.ClassFilterAwareUnionMethodMatcher
      implements IntroductionAwareMethodMatcher {
      public ClassFilterAwareUnionIntroductionAwareMethodMatcher(MethodMatcher mm1, ClassFilter cf1, MethodMatcher mm2, ClassFilter cf2) {
         super(mm1, cf1, mm2, cf2);
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
         return this.matchesClass1(targetClass) && MethodMatchers.matches(this.mm1, method, targetClass, hasIntroductions)
            || this.matchesClass2(targetClass) && MethodMatchers.matches(this.mm2, method, targetClass, hasIntroductions);
      }
   }

   private static class ClassFilterAwareUnionMethodMatcher extends MethodMatchers.UnionMethodMatcher {
      private final ClassFilter cf1;
      private final ClassFilter cf2;

      public ClassFilterAwareUnionMethodMatcher(MethodMatcher mm1, ClassFilter cf1, MethodMatcher mm2, ClassFilter cf2) {
         super(mm1, mm2);
         this.cf1 = cf1;
         this.cf2 = cf2;
      }

      @Override
      protected boolean matchesClass1(Class<?> targetClass) {
         return this.cf1.matches(targetClass);
      }

      @Override
      protected boolean matchesClass2(Class<?> targetClass) {
         return this.cf2.matches(targetClass);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         if (this == other) {
            return true;
         }

         if (!super.equals(other)) {
            return false;
         }

         ClassFilter otherCf1 = ClassFilter.TRUE;
         ClassFilter otherCf2 = ClassFilter.TRUE;
         if (other instanceof MethodMatchers.ClassFilterAwareUnionMethodMatcher cfa) {
            otherCf1 = cfa.cf1;
            otherCf2 = cfa.cf2;
         }

         return this.cf1.equals(otherCf1) && this.cf2.equals(otherCf2);
      }

      @Override
      public int hashCode() {
         return super.hashCode();
      }

      @Override
      public String toString() {
         return this.getClass().getName() + ": " + this.cf1 + ", " + this.mm1 + ", " + this.cf2 + ", " + this.mm2;
      }
   }

   private static class IntersectionIntroductionAwareMethodMatcher extends MethodMatchers.IntersectionMethodMatcher implements IntroductionAwareMethodMatcher {
      public IntersectionIntroductionAwareMethodMatcher(MethodMatcher mm1, MethodMatcher mm2) {
         super(mm1, mm2);
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
         return MethodMatchers.matches(this.mm1, method, targetClass, hasIntroductions)
            && MethodMatchers.matches(this.mm2, method, targetClass, hasIntroductions);
      }
   }

   private static class IntersectionMethodMatcher implements MethodMatcher, Serializable {
      protected final MethodMatcher mm1;
      protected final MethodMatcher mm2;

      public IntersectionMethodMatcher(MethodMatcher mm1, MethodMatcher mm2) {
         Assert.notNull(mm1, "First MethodMatcher must not be null");
         Assert.notNull(mm2, "Second MethodMatcher must not be null");
         this.mm1 = mm1;
         this.mm2 = mm2;
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass) {
         return this.mm1.matches(method, targetClass) && this.mm2.matches(method, targetClass);
      }

      @Override
      public boolean isRuntime() {
         return this.mm1.isRuntime() || this.mm2.isRuntime();
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
         boolean aMatches = this.mm1.isRuntime() ? this.mm1.matches(method, targetClass, args) : this.mm1.matches(method, targetClass);
         boolean bMatches = this.mm2.isRuntime() ? this.mm2.matches(method, targetClass, args) : this.mm2.matches(method, targetClass);
         return aMatches && bMatches;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof MethodMatchers.IntersectionMethodMatcher that && this.mm1.equals(that.mm1) && this.mm2.equals(that.mm2);
      }

      @Override
      public int hashCode() {
         return 37 * this.mm1.hashCode() + this.mm2.hashCode();
      }

      @Override
      public String toString() {
         return this.getClass().getName() + ": " + this.mm1 + ", " + this.mm2;
      }
   }

   private static class NegateMethodMatcher implements MethodMatcher, Serializable {
      private final MethodMatcher original;

      NegateMethodMatcher(MethodMatcher original) {
         this.original = original;
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass) {
         return !this.original.matches(method, targetClass);
      }

      @Override
      public boolean isRuntime() {
         return this.original.isRuntime();
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
         return !this.original.matches(method, targetClass, args);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof MethodMatchers.NegateMethodMatcher that && this.original.equals(that.original);
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.getClass(), this.original);
      }

      @Override
      public String toString() {
         return "Negate " + this.original;
      }
   }

   private static class UnionIntroductionAwareMethodMatcher extends MethodMatchers.UnionMethodMatcher implements IntroductionAwareMethodMatcher {
      public UnionIntroductionAwareMethodMatcher(MethodMatcher mm1, MethodMatcher mm2) {
         super(mm1, mm2);
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
         return this.matchesClass1(targetClass) && MethodMatchers.matches(this.mm1, method, targetClass, hasIntroductions)
            || this.matchesClass2(targetClass) && MethodMatchers.matches(this.mm2, method, targetClass, hasIntroductions);
      }
   }

   private static class UnionMethodMatcher implements MethodMatcher, Serializable {
      protected final MethodMatcher mm1;
      protected final MethodMatcher mm2;

      public UnionMethodMatcher(MethodMatcher mm1, MethodMatcher mm2) {
         Assert.notNull(mm1, "First MethodMatcher must not be null");
         Assert.notNull(mm2, "Second MethodMatcher must not be null");
         this.mm1 = mm1;
         this.mm2 = mm2;
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass) {
         return this.matchesClass1(targetClass) && this.mm1.matches(method, targetClass)
            || this.matchesClass2(targetClass) && this.mm2.matches(method, targetClass);
      }

      protected boolean matchesClass1(Class<?> targetClass) {
         return true;
      }

      protected boolean matchesClass2(Class<?> targetClass) {
         return true;
      }

      @Override
      public boolean isRuntime() {
         return this.mm1.isRuntime() || this.mm2.isRuntime();
      }

      @Override
      public boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
         return this.mm1.matches(method, targetClass, args) || this.mm2.matches(method, targetClass, args);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof MethodMatchers.UnionMethodMatcher that && this.mm1.equals(that.mm1) && this.mm2.equals(that.mm2);
      }

      @Override
      public int hashCode() {
         return 37 * this.mm1.hashCode() + this.mm2.hashCode();
      }

      @Override
      public String toString() {
         return this.getClass().getName() + ": " + this.mm1 + ", " + this.mm2;
      }
   }
}
