package org.springframework.aop.support;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public abstract class ClassFilters {
   public static ClassFilter union(ClassFilter cf1, ClassFilter cf2) {
      Assert.notNull(cf1, "First ClassFilter must not be null");
      Assert.notNull(cf2, "Second ClassFilter must not be null");
      return new ClassFilters.UnionClassFilter(new ClassFilter[]{cf1, cf2});
   }

   public static ClassFilter union(ClassFilter[] classFilters) {
      Assert.notEmpty(classFilters, "ClassFilter array must not be empty");
      return new ClassFilters.UnionClassFilter(classFilters);
   }

   public static ClassFilter intersection(ClassFilter cf1, ClassFilter cf2) {
      Assert.notNull(cf1, "First ClassFilter must not be null");
      Assert.notNull(cf2, "Second ClassFilter must not be null");
      return new ClassFilters.IntersectionClassFilter(new ClassFilter[]{cf1, cf2});
   }

   public static ClassFilter intersection(ClassFilter[] classFilters) {
      Assert.notEmpty(classFilters, "ClassFilter array must not be empty");
      return new ClassFilters.IntersectionClassFilter(classFilters);
   }

   public static ClassFilter negate(ClassFilter classFilter) {
      Assert.notNull(classFilter, "ClassFilter must not be null");
      return new ClassFilters.NegateClassFilter(classFilter);
   }

   private static class IntersectionClassFilter implements ClassFilter, Serializable {
      private final ClassFilter[] filters;

      IntersectionClassFilter(ClassFilter[] filters) {
         this.filters = filters;
      }

      @Override
      public boolean matches(Class<?> clazz) {
         for (ClassFilter filter : this.filters) {
            if (!filter.matches(clazz)) {
               return false;
            }
         }

         return true;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof ClassFilters.IntersectionClassFilter that && ObjectUtils.nullSafeEquals(this.filters, that.filters);
      }

      @Override
      public int hashCode() {
         return Arrays.hashCode(this.filters);
      }

      @Override
      public String toString() {
         return this.getClass().getName() + ": " + Arrays.toString(this.filters);
      }
   }

   private static class NegateClassFilter implements ClassFilter, Serializable {
      private final ClassFilter original;

      NegateClassFilter(ClassFilter original) {
         this.original = original;
      }

      @Override
      public boolean matches(Class<?> clazz) {
         return !this.original.matches(clazz);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof ClassFilters.NegateClassFilter that && this.original.equals(that.original);
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

   private static class UnionClassFilter implements ClassFilter, Serializable {
      private final ClassFilter[] filters;

      UnionClassFilter(ClassFilter[] filters) {
         this.filters = filters;
      }

      @Override
      public boolean matches(Class<?> clazz) {
         for (ClassFilter filter : this.filters) {
            if (filter.matches(clazz)) {
               return true;
            }
         }

         return false;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof ClassFilters.UnionClassFilter that && ObjectUtils.nullSafeEquals(this.filters, that.filters);
      }

      @Override
      public int hashCode() {
         return Arrays.hashCode(this.filters);
      }

      @Override
      public String toString() {
         return this.getClass().getName() + ": " + Arrays.toString(this.filters);
      }
   }
}
