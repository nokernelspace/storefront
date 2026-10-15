package org.springframework.core.type;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotationSelectors;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.MultiValueMap;

public interface AnnotatedTypeMetadata {
   MergedAnnotations getAnnotations();

   default boolean isAnnotated(String annotationName) {
      return this.getAnnotations().isPresent(annotationName);
   }

   default @Nullable Map<String, @Nullable Object> getAnnotationAttributes(String annotationName) {
      return this.getAnnotationAttributes(annotationName, false);
   }

   default @Nullable Map<String, @Nullable Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
      MergedAnnotation<Annotation> annotation = this.getAnnotations().get(annotationName, null, MergedAnnotationSelectors.firstDirectlyDeclared());
      return !annotation.isPresent() ? null : annotation.asAnnotationAttributes(MergedAnnotation.Adapt.values(classValuesAsString, true));
   }

   default @Nullable MultiValueMap<String, @Nullable Object> getAllAnnotationAttributes(String annotationName) {
      return this.getAllAnnotationAttributes(annotationName, false);
   }

   default @Nullable MultiValueMap<String, @Nullable Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
      MergedAnnotation.Adapt[] adaptations = MergedAnnotation.Adapt.values(classValuesAsString, true);
      return this.getAnnotations()
         .stream(annotationName)
         .filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
         .map(MergedAnnotation::withNonMergedAttributes)
         .collect(MergedAnnotationCollectors.toMultiValueMap(map -> map.isEmpty() ? null : map, adaptations));
   }

   default Set<AnnotationAttributes> getMergedRepeatableAnnotationAttributes(
      Class<? extends Annotation> annotationType, Class<? extends Annotation> containerType, boolean classValuesAsString
   ) {
      return this.getMergedRepeatableAnnotationAttributes(annotationType, containerType, classValuesAsString, false);
   }

   default Set<AnnotationAttributes> getMergedRepeatableAnnotationAttributes(
      Class<? extends Annotation> annotationType, Class<? extends Annotation> containerType, boolean classValuesAsString, boolean sortByReversedMetaDistance
   ) {
      return this.getMergedRepeatableAnnotationAttributes(
         annotationType, containerType, mergedAnnotation -> true, classValuesAsString, sortByReversedMetaDistance
      );
   }

   default Set<AnnotationAttributes> getMergedRepeatableAnnotationAttributes(
      Class<? extends Annotation> annotationType,
      Class<? extends Annotation> containerType,
      Predicate<MergedAnnotation<? extends Annotation>> predicate,
      boolean classValuesAsString,
      boolean sortByReversedMetaDistance
   ) {
      Stream<MergedAnnotation<Annotation>> stream = this.getAnnotations()
         .stream()
         .filter(predicate)
         .filter(MergedAnnotationPredicates.typeIn(containerType, annotationType));
      if (sortByReversedMetaDistance) {
         stream = stream.sorted(reversedMetaDistance());
      }

      MergedAnnotation.Adapt[] adaptations = MergedAnnotation.Adapt.values(classValuesAsString, true);
      return stream.<AnnotationAttributes>map(annotation -> annotation.asAnnotationAttributes(adaptations))
         .flatMap(
            attributes -> containerType.equals(attributes.annotationType())
               ? Stream.of(attributes.getAnnotationArray("value"))
               : Stream.of((AnnotationAttributes)attributes)
         )
         .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   private static Comparator<MergedAnnotation<Annotation>> reversedMetaDistance() {
      return Comparator.comparingInt(MergedAnnotation::getDistance).reversed();
   }
}
