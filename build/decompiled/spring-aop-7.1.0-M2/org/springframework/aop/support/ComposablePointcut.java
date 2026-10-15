package org.springframework.aop.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.util.Assert;

public class ComposablePointcut implements Pointcut, Serializable {
   private static final long serialVersionUID = -2743223737633663832L;
   private ClassFilter classFilter;
   private MethodMatcher methodMatcher;

   public ComposablePointcut() {
      this.classFilter = ClassFilter.TRUE;
      this.methodMatcher = MethodMatcher.TRUE;
   }

   public ComposablePointcut(Pointcut pointcut) {
      Assert.notNull(pointcut, "Pointcut must not be null");
      this.classFilter = pointcut.getClassFilter();
      this.methodMatcher = pointcut.getMethodMatcher();
   }

   public ComposablePointcut(ClassFilter classFilter) {
      Assert.notNull(classFilter, "ClassFilter must not be null");
      this.classFilter = classFilter;
      this.methodMatcher = MethodMatcher.TRUE;
   }

   public ComposablePointcut(MethodMatcher methodMatcher) {
      Assert.notNull(methodMatcher, "MethodMatcher must not be null");
      this.classFilter = ClassFilter.TRUE;
      this.methodMatcher = methodMatcher;
   }

   public ComposablePointcut(ClassFilter classFilter, MethodMatcher methodMatcher) {
      Assert.notNull(classFilter, "ClassFilter must not be null");
      Assert.notNull(methodMatcher, "MethodMatcher must not be null");
      this.classFilter = classFilter;
      this.methodMatcher = methodMatcher;
   }

   public ComposablePointcut union(ClassFilter other) {
      this.classFilter = ClassFilters.union(this.classFilter, other);
      return this;
   }

   public ComposablePointcut intersection(ClassFilter other) {
      this.classFilter = ClassFilters.intersection(this.classFilter, other);
      return this;
   }

   public ComposablePointcut union(MethodMatcher other) {
      this.methodMatcher = MethodMatchers.union(this.methodMatcher, other);
      return this;
   }

   public ComposablePointcut intersection(MethodMatcher other) {
      this.methodMatcher = MethodMatchers.intersection(this.methodMatcher, other);
      return this;
   }

   public ComposablePointcut union(Pointcut other) {
      this.methodMatcher = MethodMatchers.union(this.methodMatcher, this.classFilter, other.getMethodMatcher(), other.getClassFilter());
      this.classFilter = ClassFilters.union(this.classFilter, other.getClassFilter());
      return this;
   }

   public ComposablePointcut intersection(Pointcut other) {
      this.classFilter = ClassFilters.intersection(this.classFilter, other.getClassFilter());
      this.methodMatcher = MethodMatchers.intersection(this.methodMatcher, other.getMethodMatcher());
      return this;
   }

   @Override
   public ClassFilter getClassFilter() {
      return this.classFilter;
   }

   @Override
   public MethodMatcher getMethodMatcher() {
      return this.methodMatcher;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ComposablePointcut otherPointcut
            && this.classFilter.equals(otherPointcut.classFilter)
            && this.methodMatcher.equals(otherPointcut.methodMatcher);
   }

   @Override
   public int hashCode() {
      return this.classFilter.hashCode() * 37 + this.methodMatcher.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.classFilter + ", " + this.methodMatcher;
   }
}
