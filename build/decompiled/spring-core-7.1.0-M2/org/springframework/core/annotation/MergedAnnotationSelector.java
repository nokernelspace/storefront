package org.springframework.core.annotation;

import java.lang.annotation.Annotation;

@FunctionalInterface
public interface MergedAnnotationSelector<A extends Annotation> {
   default boolean isBestCandidate(MergedAnnotation<A> annotation) {
      return false;
   }

   MergedAnnotation<A> select(MergedAnnotation<A> existing, MergedAnnotation<A> candidate);
}
