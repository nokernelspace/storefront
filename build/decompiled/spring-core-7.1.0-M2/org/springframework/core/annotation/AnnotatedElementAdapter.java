package org.springframework.core.annotation;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

public final class AnnotatedElementAdapter implements AnnotatedElement, Serializable {
   private static final AnnotatedElementAdapter EMPTY = new AnnotatedElementAdapter(new Annotation[0]);
   private final Annotation[] annotations;

   public static AnnotatedElementAdapter from(Annotation @Nullable [] annotations) {
      return annotations != null && annotations.length != 0 ? new AnnotatedElementAdapter(annotations) : EMPTY;
   }

   private AnnotatedElementAdapter(Annotation[] annotations) {
      this.annotations = annotations;
   }

   @Override
   public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
      for (Annotation annotation : this.annotations) {
         if (annotation.annotationType() == annotationClass) {
            return true;
         }
      }

      return false;
   }

   @Override
   public <A extends Annotation> @Nullable A getAnnotation(Class<A> annotationClass) {
      for (Annotation annotation : this.annotations) {
         if (annotation.annotationType() == annotationClass) {
            return annotationClass.cast(annotation);
         }
      }

      return null;
   }

   @Override
   public Annotation[] getAnnotations() {
      return this.isEmpty() ? this.annotations : (Annotation[])this.annotations.clone();
   }

   @Override
   public <A extends Annotation> @Nullable A getDeclaredAnnotation(Class<A> annotationClass) {
      return this.getAnnotation(annotationClass);
   }

   @Override
   public Annotation[] getDeclaredAnnotations() {
      return this.getAnnotations();
   }

   public boolean isEmpty() {
      return this == EMPTY;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof AnnotatedElementAdapter that && Arrays.equals(this.annotations, that.annotations);
   }

   @Override
   public int hashCode() {
      return Arrays.hashCode(this.annotations);
   }

   @Override
   public String toString() {
      return Arrays.toString(this.annotations);
   }
}
