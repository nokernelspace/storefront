package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.MultiValueMap;

public abstract class AnnotatedElementUtils {
   public static AnnotatedElement forAnnotations(Annotation... annotations) {
      return AnnotatedElementAdapter.from(annotations);
   }

   public static Set<String> getMetaAnnotationTypes(AnnotatedElement element, Class<? extends Annotation> annotationType) {
      return getMetaAnnotationTypes(element, element.getAnnotation(annotationType));
   }

   public static Set<String> getMetaAnnotationTypes(AnnotatedElement element, String annotationName) {
      for (Annotation annotation : element.getAnnotations()) {
         if (annotation.annotationType().getName().equals(annotationName)) {
            return getMetaAnnotationTypes(element, annotation);
         }
      }

      return Collections.emptySet();
   }

   private static Set<String> getMetaAnnotationTypes(AnnotatedElement element, @Nullable Annotation annotation) {
      return annotation == null
         ? Collections.emptySet()
         : getAnnotations(annotation.annotationType())
            .stream()
            .map(mergedAnnotation -> mergedAnnotation.getType().getName())
            .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   public static boolean hasMetaAnnotationTypes(AnnotatedElement element, Class<? extends Annotation> annotationType) {
      return getAnnotations(element).stream(annotationType).anyMatch(MergedAnnotation::isMetaPresent);
   }

   public static boolean hasMetaAnnotationTypes(AnnotatedElement element, String annotationName) {
      return getAnnotations(element).stream(annotationName).anyMatch(MergedAnnotation::isMetaPresent);
   }

   public static boolean isAnnotated(AnnotatedElement element, Class<? extends Annotation> annotationType) {
      return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(element)
         ? getAnnotations(element).isPresent(annotationType)
         : element.isAnnotationPresent(annotationType);
   }

   public static boolean isAnnotated(AnnotatedElement element, String annotationName) {
      return getAnnotations(element).isPresent(annotationName);
   }

   public static @Nullable AnnotationAttributes getMergedAnnotationAttributes(AnnotatedElement element, Class<? extends Annotation> annotationType) {
      MergedAnnotation<?> mergedAnnotation = getAnnotations(element).get(annotationType, null, MergedAnnotationSelectors.firstDirectlyDeclared());
      return getAnnotationAttributes(mergedAnnotation, false, false);
   }

   public static @Nullable AnnotationAttributes getMergedAnnotationAttributes(AnnotatedElement element, String annotationName) {
      return getMergedAnnotationAttributes(element, annotationName, false, false);
   }

   public static @Nullable AnnotationAttributes getMergedAnnotationAttributes(
      AnnotatedElement element, String annotationName, boolean classValuesAsString, boolean nestedAnnotationsAsMap
   ) {
      MergedAnnotation<?> mergedAnnotation = getAnnotations(element).get(annotationName, null, MergedAnnotationSelectors.firstDirectlyDeclared());
      return getAnnotationAttributes(mergedAnnotation, classValuesAsString, nestedAnnotationsAsMap);
   }

   public static <A extends Annotation> @Nullable A getMergedAnnotation(AnnotatedElement element, Class<A> annotationType) {
      return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(element)
         ? getAnnotations(element)
            .get(annotationType, null, MergedAnnotationSelectors.firstDirectlyDeclared())
            .synthesize(MergedAnnotation::isPresent)
            .orElse(null)
         : element.getDeclaredAnnotation(annotationType);
   }

   public static <A extends Annotation> Set<A> getAllMergedAnnotations(AnnotatedElement element, Class<A> annotationType) {
      return getAnnotations(element).stream(annotationType).collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   public static Set<Annotation> getAllMergedAnnotations(AnnotatedElement element, Set<Class<? extends Annotation>> annotationTypes) {
      return getAnnotations(element).stream().filter(MergedAnnotationPredicates.typeIn(annotationTypes)).collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   public static <A extends Annotation> Set<A> getMergedRepeatableAnnotations(AnnotatedElement element, Class<A> annotationType) {
      return getMergedRepeatableAnnotations(element, annotationType, null);
   }

   public static <A extends Annotation> Set<A> getMergedRepeatableAnnotations(
      AnnotatedElement element, Class<A> annotationType, @Nullable Class<? extends Annotation> containerType
   ) {
      return getRepeatableAnnotations(element, annotationType, containerType).stream(annotationType).collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   public static @Nullable MultiValueMap<String, @Nullable Object> getAllAnnotationAttributes(AnnotatedElement element, String annotationName) {
      return getAllAnnotationAttributes(element, annotationName, false, false);
   }

   public static @Nullable MultiValueMap<String, @Nullable Object> getAllAnnotationAttributes(
      AnnotatedElement element, String annotationName, final boolean classValuesAsString, final boolean nestedAnnotationsAsMap
   ) {
      MergedAnnotation.Adapt[] adaptations = MergedAnnotation.Adapt.values(classValuesAsString, nestedAnnotationsAsMap);
      return getAnnotations(element)
         .stream(annotationName)
         .filter(MergedAnnotationPredicates.unique(MergedAnnotation::getMetaTypes))
         .map(MergedAnnotation::withNonMergedAttributes)
         .collect(MergedAnnotationCollectors.toMultiValueMap(AnnotatedElementUtils::nullIfEmpty, adaptations));
   }

   public static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
      return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(element)
         ? findAnnotations(element).isPresent(annotationType)
         : element.isAnnotationPresent(annotationType);
   }

   public static @Nullable AnnotationAttributes findMergedAnnotationAttributes(
      AnnotatedElement element, Class<? extends Annotation> annotationType, boolean classValuesAsString, boolean nestedAnnotationsAsMap
   ) {
      MergedAnnotation<?> mergedAnnotation = findAnnotations(element).get(annotationType, null, MergedAnnotationSelectors.firstDirectlyDeclared());
      return getAnnotationAttributes(mergedAnnotation, classValuesAsString, nestedAnnotationsAsMap);
   }

   public static @Nullable AnnotationAttributes findMergedAnnotationAttributes(
      AnnotatedElement element, String annotationName, boolean classValuesAsString, boolean nestedAnnotationsAsMap
   ) {
      MergedAnnotation<?> mergedAnnotation = findAnnotations(element).get(annotationName, null, MergedAnnotationSelectors.firstDirectlyDeclared());
      return getAnnotationAttributes(mergedAnnotation, classValuesAsString, nestedAnnotationsAsMap);
   }

   public static <A extends Annotation> @Nullable A findMergedAnnotation(AnnotatedElement element, Class<A> annotationType) {
      return !AnnotationFilter.PLAIN.matches(annotationType) && !AnnotationsScanner.hasPlainJavaAnnotationsOnly(element)
         ? findAnnotations(element)
            .get(annotationType, null, MergedAnnotationSelectors.firstDirectlyDeclared())
            .synthesize(MergedAnnotation::isPresent)
            .orElse(null)
         : element.getDeclaredAnnotation(annotationType);
   }

   public static <A extends Annotation> Set<A> findAllMergedAnnotations(AnnotatedElement element, Class<A> annotationType) {
      return findAnnotations(element).stream(annotationType).sorted(highAggregateIndexesFirst()).collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   public static Set<Annotation> findAllMergedAnnotations(AnnotatedElement element, Set<Class<? extends Annotation>> annotationTypes) {
      return findAnnotations(element)
         .stream()
         .filter(MergedAnnotationPredicates.typeIn(annotationTypes))
         .sorted(highAggregateIndexesFirst())
         .collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   public static <A extends Annotation> Set<A> findMergedRepeatableAnnotations(AnnotatedElement element, Class<A> annotationType) {
      return findMergedRepeatableAnnotations(element, annotationType, null);
   }

   public static <A extends Annotation> Set<A> findMergedRepeatableAnnotations(
      AnnotatedElement element, Class<A> annotationType, @Nullable Class<? extends Annotation> containerType
   ) {
      return findRepeatableAnnotations(element, annotationType, containerType)
         .stream(annotationType)
         .sorted(highAggregateIndexesFirst())
         .collect(MergedAnnotationCollectors.toAnnotationSet());
   }

   private static MergedAnnotations getAnnotations(AnnotatedElement element) {
      return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none());
   }

   private static MergedAnnotations getRepeatableAnnotations(
      AnnotatedElement element, Class<? extends Annotation> annotationType, @Nullable Class<? extends Annotation> containerType
   ) {
      RepeatableContainers repeatableContainers;
      if (containerType == null) {
         RepeatableContainers.explicitRepeatable(annotationType, null);
         repeatableContainers = RepeatableContainers.standardRepeatables();
      } else {
         repeatableContainers = RepeatableContainers.explicitRepeatable(annotationType, containerType);
      }

      return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, repeatableContainers);
   }

   private static MergedAnnotations findAnnotations(AnnotatedElement element) {
      return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none());
   }

   private static MergedAnnotations findRepeatableAnnotations(
      AnnotatedElement element, Class<? extends Annotation> annotationType, @Nullable Class<? extends Annotation> containerType
   ) {
      RepeatableContainers repeatableContainers;
      if (containerType == null) {
         RepeatableContainers.explicitRepeatable(annotationType, null);
         repeatableContainers = RepeatableContainers.standardRepeatables();
      } else {
         repeatableContainers = RepeatableContainers.explicitRepeatable(annotationType, containerType);
      }

      return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, repeatableContainers);
   }

   private static @Nullable MultiValueMap<String, @Nullable Object> nullIfEmpty(MultiValueMap<String, @Nullable Object> map) {
      return map.isEmpty() ? null : map;
   }

   private static <A extends Annotation> Comparator<MergedAnnotation<A>> highAggregateIndexesFirst() {
      return Comparator.comparingInt(MergedAnnotation::getAggregateIndex).reversed();
   }

   private static @Nullable AnnotationAttributes getAnnotationAttributes(
      MergedAnnotation<?> annotation, boolean classValuesAsString, boolean nestedAnnotationsAsMap
   ) {
      return !annotation.isPresent() ? null : annotation.asAnnotationAttributes(MergedAnnotation.Adapt.values(classValuesAsString, nestedAnnotationsAsMap));
   }
}
