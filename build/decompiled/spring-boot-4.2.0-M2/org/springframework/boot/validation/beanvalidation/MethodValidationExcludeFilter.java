package org.springframework.boot.validation.beanvalidation;

import java.lang.annotation.Annotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

public interface MethodValidationExcludeFilter {
   boolean isExcluded(Class<?> type);

   static MethodValidationExcludeFilter byAnnotation(Class<? extends Annotation> annotationType) {
      return byAnnotation(annotationType, SearchStrategy.INHERITED_ANNOTATIONS);
   }

   static MethodValidationExcludeFilter byAnnotation(Class<? extends Annotation> annotationType, SearchStrategy searchStrategy) {
      return type -> MergedAnnotations.from(type, searchStrategy).isPresent(annotationType);
   }
}
