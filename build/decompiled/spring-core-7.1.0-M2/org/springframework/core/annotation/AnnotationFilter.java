package org.springframework.core.annotation;

import java.lang.annotation.Annotation;

@FunctionalInterface
public interface AnnotationFilter {
   AnnotationFilter PLAIN = packages("java.lang", "org.springframework.lang");
   AnnotationFilter JAVA = packages("java", "javax");
   AnnotationFilter ALL = new AnnotationFilter() {
      @Override
      public boolean matches(Annotation annotation) {
         return true;
      }

      @Override
      public boolean matches(Class<?> type) {
         return true;
      }

      @Override
      public boolean matches(String typeName) {
         return true;
      }

      @Override
      public String toString() {
         return "All annotations filtered";
      }
   };
   @Deprecated(since = "5.2.6")
   AnnotationFilter NONE = new AnnotationFilter() {
      @Override
      public boolean matches(Annotation annotation) {
         return false;
      }

      @Override
      public boolean matches(Class<?> type) {
         return false;
      }

      @Override
      public boolean matches(String typeName) {
         return false;
      }

      @Override
      public String toString() {
         return "No annotation filtering";
      }
   };

   default boolean matches(Annotation annotation) {
      return this.matches(annotation.annotationType());
   }

   default boolean matches(Class<?> type) {
      return this.matches(type.getName());
   }

   boolean matches(String typeName);

   static AnnotationFilter packages(String... packages) {
      return new PackagesAnnotationFilter(packages);
   }
}
