package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface AnnotationsProcessor<C, R> {
   default @Nullable R doWithAggregate(C context, int aggregateIndex) {
      return null;
   }

   @Nullable R doWithAnnotations(C context, int aggregateIndex, @Nullable Object source, @Nullable Annotation[] annotations);

   default @Nullable R finish(@Nullable R result) {
      return result;
   }
}
