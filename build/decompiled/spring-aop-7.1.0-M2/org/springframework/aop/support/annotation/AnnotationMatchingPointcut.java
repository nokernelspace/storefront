package org.springframework.aop.support.annotation;

import java.lang.annotation.Annotation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

public class AnnotationMatchingPointcut implements Pointcut {
   private final ClassFilter classFilter;
   private final MethodMatcher methodMatcher;

   public AnnotationMatchingPointcut(Class<? extends Annotation> classAnnotationType) {
      this(classAnnotationType, false);
   }

   public AnnotationMatchingPointcut(Class<? extends Annotation> classAnnotationType, boolean checkInherited) {
      this.classFilter = new AnnotationClassFilter(classAnnotationType, checkInherited);
      this.methodMatcher = MethodMatcher.TRUE;
   }

   public AnnotationMatchingPointcut(@Nullable Class<? extends Annotation> classAnnotationType, @Nullable Class<? extends Annotation> methodAnnotationType) {
      this(classAnnotationType, methodAnnotationType, false);
   }

   public AnnotationMatchingPointcut(
      @Nullable Class<? extends Annotation> classAnnotationType, @Nullable Class<? extends Annotation> methodAnnotationType, boolean checkInherited
   ) {
      Assert.isTrue(
         classAnnotationType != null || methodAnnotationType != null, "Either Class annotation type or Method annotation type needs to be specified (or both)"
      );
      if (classAnnotationType != null) {
         this.classFilter = new AnnotationClassFilter(classAnnotationType, checkInherited);
      } else {
         this.classFilter = new AnnotationMatchingPointcut.AnnotationCandidateClassFilter(methodAnnotationType);
      }

      if (methodAnnotationType != null) {
         this.methodMatcher = new AnnotationMethodMatcher(methodAnnotationType, checkInherited);
      } else {
         this.methodMatcher = MethodMatcher.TRUE;
      }
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
         || other instanceof AnnotationMatchingPointcut otherPointcut
            && this.classFilter.equals(otherPointcut.classFilter)
            && this.methodMatcher.equals(otherPointcut.methodMatcher);
   }

   @Override
   public int hashCode() {
      return this.classFilter.hashCode() * 37 + this.methodMatcher.hashCode();
   }

   @Override
   public String toString() {
      return "AnnotationMatchingPointcut: " + this.classFilter + ", " + this.methodMatcher;
   }

   public static AnnotationMatchingPointcut forClassAnnotation(Class<? extends Annotation> annotationType) {
      Assert.notNull(annotationType, "Annotation type must not be null");
      return new AnnotationMatchingPointcut(annotationType);
   }

   public static AnnotationMatchingPointcut forMethodAnnotation(Class<? extends Annotation> annotationType) {
      Assert.notNull(annotationType, "Annotation type must not be null");
      return new AnnotationMatchingPointcut(null, annotationType);
   }

   private static class AnnotationCandidateClassFilter implements ClassFilter {
      private final Class<? extends Annotation> annotationType;

      AnnotationCandidateClassFilter(Class<? extends Annotation> annotationType) {
         this.annotationType = annotationType;
      }

      @Override
      public boolean matches(Class<?> clazz) {
         return AnnotationUtils.isCandidateClass(clazz, this.annotationType);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof AnnotationMatchingPointcut.AnnotationCandidateClassFilter that && this.annotationType.equals(that.annotationType);
      }

      @Override
      public int hashCode() {
         return this.annotationType.hashCode();
      }

      @Override
      public String toString() {
         return this.getClass().getName() + ": " + this.annotationType;
      }
   }
}
