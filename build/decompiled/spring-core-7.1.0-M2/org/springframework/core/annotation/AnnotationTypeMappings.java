package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

final class AnnotationTypeMappings {
   private static final IntrospectionFailureLogger failureLogger = IntrospectionFailureLogger.DEBUG;
   private static final Map<AnnotationFilter, AnnotationTypeMappings.Cache> standardRepeatablesCache = new ConcurrentReferenceHashMap<>();
   private static final Map<AnnotationFilter, AnnotationTypeMappings.Cache> noRepeatablesCache = new ConcurrentReferenceHashMap<>();
   private final RepeatableContainers repeatableContainers;
   private final AnnotationFilter filter;
   private final List<AnnotationTypeMapping> mappings;

   private AnnotationTypeMappings(
      RepeatableContainers repeatableContainers,
      AnnotationFilter filter,
      Class<? extends Annotation> annotationType,
      Set<Class<? extends Annotation>> visitedAnnotationTypes
   ) {
      this.repeatableContainers = repeatableContainers;
      this.filter = filter;
      this.mappings = new ArrayList<>();
      this.addAllMappings(annotationType, visitedAnnotationTypes);
      this.mappings.forEach(AnnotationTypeMapping::afterAllMappingsSet);
   }

   private void addAllMappings(Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visitedAnnotationTypes) {
      Deque<AnnotationTypeMapping> queue = new ArrayDeque<>();
      this.addIfPossible(queue, null, annotationType, null, false, visitedAnnotationTypes);

      while (!queue.isEmpty()) {
         AnnotationTypeMapping mapping = queue.removeFirst();
         this.mappings.add(mapping);
         this.addMetaAnnotationsToQueue(queue, mapping);
      }
   }

   private void addMetaAnnotationsToQueue(Deque<AnnotationTypeMapping> queue, AnnotationTypeMapping source) {
      Annotation[] metaAnnotations = AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false);

      for (Annotation metaAnnotation : metaAnnotations) {
         if (this.isMappable(source, metaAnnotation)) {
            Annotation[] repeatedAnnotations = this.repeatableContainers.findRepeatedAnnotations(metaAnnotation);
            if (repeatedAnnotations != null) {
               for (Annotation repeatedAnnotation : repeatedAnnotations) {
                  if (this.isMappable(source, repeatedAnnotation)) {
                     this.addIfPossible(queue, source, repeatedAnnotation);
                  }
               }
            } else {
               this.addIfPossible(queue, source, metaAnnotation);
            }
         }
      }
   }

   private void addIfPossible(Deque<AnnotationTypeMapping> queue, AnnotationTypeMapping source, Annotation ann) {
      this.addIfPossible(queue, source, ann.annotationType(), ann, true, new HashSet<>());
   }

   private void addIfPossible(
      Deque<AnnotationTypeMapping> queue,
      @Nullable AnnotationTypeMapping source,
      Class<? extends Annotation> annotationType,
      @Nullable Annotation ann,
      boolean meta,
      Set<Class<? extends Annotation>> visitedAnnotationTypes
   ) {
      try {
         queue.addLast(new AnnotationTypeMapping(source, annotationType, ann, visitedAnnotationTypes));
      } catch (Exception ex) {
         AnnotationUtils.rethrowAnnotationConfigurationException(ex);
         if (failureLogger.isEnabled()) {
            failureLogger.log(
               "Failed to introspect " + (meta ? "meta-annotation @" : "annotation @") + ClassUtils.getCanonicalName(annotationType),
               source != null ? ClassUtils.getCanonicalName(source.getAnnotationType()) : null,
               ex
            );
         }
      }
   }

   @Contract("_, null -> false")
   private boolean isMappable(AnnotationTypeMapping source, @Nullable Annotation metaAnnotation) {
      return metaAnnotation != null
         && !this.filter.matches(metaAnnotation)
         && !AnnotationFilter.PLAIN.matches(source.getAnnotationType())
         && !this.isAlreadyMapped(source, metaAnnotation);
   }

   private boolean isAlreadyMapped(AnnotationTypeMapping source, Annotation metaAnnotation) {
      Class<? extends Annotation> annotationType = metaAnnotation.annotationType();

      for (AnnotationTypeMapping mapping = source; mapping != null; mapping = mapping.getSource()) {
         if (mapping.getAnnotationType() == annotationType) {
            return true;
         }
      }

      return false;
   }

   int size() {
      return this.mappings.size();
   }

   AnnotationTypeMapping get(int index) {
      return this.mappings.get(index);
   }

   static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType) {
      return forAnnotationType(annotationType, RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN);
   }

   static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visitedAnnotationTypes) {
      return forAnnotationType(annotationType, RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, visitedAnnotationTypes);
   }

   static AnnotationTypeMappings forAnnotationType(
      Class<? extends Annotation> annotationType, RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter
   ) {
      AnnotationTypeMappings.Cache cache = getCache(repeatableContainers, annotationFilter);
      return cache != null ? cache.get(annotationType) : new AnnotationTypeMappings(repeatableContainers, annotationFilter, annotationType, new HashSet<>());
   }

   static AnnotationTypeMappings forAnnotationType(
      Class<? extends Annotation> annotationType,
      RepeatableContainers repeatableContainers,
      AnnotationFilter annotationFilter,
      Set<Class<? extends Annotation>> visitedAnnotationTypes
   ) {
      AnnotationTypeMappings.Cache cache = getCache(repeatableContainers, annotationFilter);
      return cache != null
         ? cache.get(annotationType, visitedAnnotationTypes)
         : new AnnotationTypeMappings(repeatableContainers, annotationFilter, annotationType, visitedAnnotationTypes);
   }

   private static AnnotationTypeMappings.@Nullable Cache getCache(RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {
      if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
         return standardRepeatablesCache.computeIfAbsent(annotationFilter, key -> new AnnotationTypeMappings.Cache(repeatableContainers, key));
      } else {
         return repeatableContainers == RepeatableContainers.none()
            ? noRepeatablesCache.computeIfAbsent(annotationFilter, key -> new AnnotationTypeMappings.Cache(repeatableContainers, key))
            : null;
      }
   }

   static void clearCache() {
      standardRepeatablesCache.clear();
      noRepeatablesCache.clear();
   }

   private static class Cache {
      private final RepeatableContainers repeatableContainers;
      private final AnnotationFilter filter;
      private final Map<Class<? extends Annotation>, AnnotationTypeMappings> mappings;

      Cache(RepeatableContainers repeatableContainers, AnnotationFilter filter) {
         this.repeatableContainers = repeatableContainers;
         this.filter = filter;
         this.mappings = new ConcurrentReferenceHashMap<>();
      }

      AnnotationTypeMappings get(Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visitedAnnotationTypes) {
         return this.getOrCreate(annotationType, visitedAnnotationTypes);
      }

      AnnotationTypeMappings get(Class<? extends Annotation> annotationType) {
         AnnotationTypeMappings result = this.mappings.get(annotationType);
         return result != null ? result : this.getOrCreate(annotationType, new HashSet<>());
      }

      private AnnotationTypeMappings getOrCreate(Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visitedAnnotationTypes) {
         AnnotationTypeMappings result = this.mappings.get(annotationType);
         if (result != null) {
            return result;
         }

         result = this.createMappings(annotationType, visitedAnnotationTypes);
         AnnotationTypeMappings existing = this.mappings.putIfAbsent(annotationType, result);
         return existing != null ? existing : result;
      }

      private AnnotationTypeMappings createMappings(Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visitedAnnotationTypes) {
         return new AnnotationTypeMappings(this.repeatableContainers, this.filter, annotationType, visitedAnnotationTypes);
      }
   }
}
