package org.springframework.core.type;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

public interface AnnotationMetadata extends ClassMetadata, AnnotatedTypeMetadata {
   default Set<String> getAnnotationTypes() {
      return this.getAnnotations()
         .stream()
         .filter(MergedAnnotation::isDirectlyPresent)
         .map(annotation -> annotation.getType().getName())
         .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   default Set<String> getMetaAnnotationTypes(String annotationName) {
      MergedAnnotation<?> annotation = this.getAnnotations().get(annotationName, MergedAnnotation::isDirectlyPresent);
      return !annotation.isPresent()
         ? Collections.emptySet()
         : MergedAnnotations.from(annotation.getType())
            .stream()
            .map(mergedAnnotation -> mergedAnnotation.getType().getName())
            .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   default boolean hasAnnotation(String annotationName) {
      return this.getAnnotations().isDirectlyPresent(annotationName);
   }

   default boolean hasMetaAnnotation(String metaAnnotationName) {
      return this.getAnnotations().get(metaAnnotationName, MergedAnnotation::isMetaPresent).isPresent();
   }

   default boolean hasAnnotatedMethods(String annotationName) {
      return !this.getAnnotatedMethods(annotationName).isEmpty();
   }

   Set<MethodMetadata> getAnnotatedMethods(String annotationName);

   Set<MethodMetadata> getDeclaredMethods();

   static AnnotationMetadata introspect(Class<?> type) {
      return StandardAnnotationMetadata.from(type);
   }
}
