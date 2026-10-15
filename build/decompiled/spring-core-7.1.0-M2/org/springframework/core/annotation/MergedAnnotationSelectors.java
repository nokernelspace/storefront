package org.springframework.core.annotation;

import java.lang.annotation.Annotation;

public abstract class MergedAnnotationSelectors {
   private static final MergedAnnotationSelector<?> NEAREST = new MergedAnnotationSelectors.Nearest();
   private static final MergedAnnotationSelector<?> FIRST_DIRECTLY_DECLARED = new MergedAnnotationSelectors.FirstDirectlyDeclared();

   private MergedAnnotationSelectors() {
   }

   public static <A extends Annotation> MergedAnnotationSelector<A> nearest() {
      return (MergedAnnotationSelector<A>)NEAREST;
   }

   public static <A extends Annotation> MergedAnnotationSelector<A> firstDirectlyDeclared() {
      return (MergedAnnotationSelector<A>)FIRST_DIRECTLY_DECLARED;
   }

   private static class FirstDirectlyDeclared implements MergedAnnotationSelector<Annotation> {
      @Override
      public boolean isBestCandidate(MergedAnnotation<Annotation> annotation) {
         return annotation.getDistance() == 0;
      }

      @Override
      public MergedAnnotation<Annotation> select(MergedAnnotation<Annotation> existing, MergedAnnotation<Annotation> candidate) {
         return existing.getDistance() > 0 && candidate.getDistance() == 0 ? candidate : existing;
      }
   }

   private static class Nearest implements MergedAnnotationSelector<Annotation> {
      @Override
      public boolean isBestCandidate(MergedAnnotation<Annotation> annotation) {
         return annotation.getDistance() == 0;
      }

      @Override
      public MergedAnnotation<Annotation> select(MergedAnnotation<Annotation> existing, MergedAnnotation<Annotation> candidate) {
         return candidate.getDistance() < existing.getDistance() ? candidate : existing;
      }
   }
}
