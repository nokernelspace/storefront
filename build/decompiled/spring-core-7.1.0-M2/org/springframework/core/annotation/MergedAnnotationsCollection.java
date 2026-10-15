package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class MergedAnnotationsCollection implements MergedAnnotations {
   private static final MergedAnnotation<?>[] EMPTY_MERGED_ANNOTATION_ARRAY = new MergedAnnotation[0];
   private final MergedAnnotation<?>[] annotations;
   private final AnnotationTypeMappings[] mappings;

   private MergedAnnotationsCollection(Collection<MergedAnnotation<?>> annotations) {
      Assert.notNull(annotations, "Annotations must not be null");
      this.annotations = annotations.toArray(EMPTY_MERGED_ANNOTATION_ARRAY);
      this.mappings = new AnnotationTypeMappings[this.annotations.length];

      for (int i = 0; i < this.annotations.length; i++) {
         MergedAnnotation<?> annotation = this.annotations[i];
         Assert.notNull(annotation, "Annotation must not be null");
         Assert.isTrue(annotation.isDirectlyPresent(), "Annotation must be directly present");
         Assert.isTrue(annotation.getAggregateIndex() == 0, "Annotation must have aggregate index of zero");
         this.mappings[i] = AnnotationTypeMappings.forAnnotationType((Class<? extends Annotation>)annotation.getType());
      }
   }

   @Override
   public Iterator<MergedAnnotation<Annotation>> iterator() {
      return Spliterators.iterator(this.spliterator());
   }

   @Override
   public Spliterator<MergedAnnotation<Annotation>> spliterator() {
      return this.spliterator(null);
   }

   private <A extends Annotation> Spliterator<MergedAnnotation<A>> spliterator(@Nullable Object annotationType) {
      return new MergedAnnotationsCollection.AnnotationsSpliterator<>(annotationType);
   }

   @Override
   public <A extends Annotation> boolean isPresent(Class<A> annotationType) {
      return this.isPresent(annotationType, false);
   }

   @Override
   public boolean isPresent(String annotationType) {
      return this.isPresent(annotationType, false);
   }

   @Override
   public <A extends Annotation> boolean isDirectlyPresent(Class<A> annotationType) {
      return this.isPresent(annotationType, true);
   }

   @Override
   public boolean isDirectlyPresent(String annotationType) {
      return this.isPresent(annotationType, true);
   }

   private boolean isPresent(Object requiredType, boolean directOnly) {
      for (MergedAnnotation<?> annotation : this.annotations) {
         Class<? extends Annotation> type = (Class<? extends Annotation>)annotation.getType();
         if (type == requiredType || type.getName().equals(requiredType)) {
            return true;
         }
      }

      if (!directOnly) {
         for (AnnotationTypeMappings mappings : this.mappings) {
            for (int i = 1; i < mappings.size(); i++) {
               AnnotationTypeMapping mapping = mappings.get(i);
               if (isMappingForType(mapping, requiredType)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public <A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType) {
      return this.get(annotationType, null, null);
   }

   @Override
   public <A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType, @Nullable Predicate<? super MergedAnnotation<A>> predicate) {
      return this.get(annotationType, predicate, null);
   }

   @Override
   public <A extends Annotation> MergedAnnotation<A> get(
      Class<A> annotationType, @Nullable Predicate<? super MergedAnnotation<A>> predicate, @Nullable MergedAnnotationSelector<A> selector
   ) {
      MergedAnnotation<A> result = this.find(annotationType, predicate, selector);
      return result != null ? result : MergedAnnotation.missing();
   }

   @Override
   public <A extends Annotation> MergedAnnotation<A> get(String annotationType) {
      return this.get(annotationType, null, null);
   }

   @Override
   public <A extends Annotation> MergedAnnotation<A> get(String annotationType, @Nullable Predicate<? super MergedAnnotation<A>> predicate) {
      return this.get(annotationType, predicate, null);
   }

   @Override
   public <A extends Annotation> MergedAnnotation<A> get(
      String annotationType, @Nullable Predicate<? super MergedAnnotation<A>> predicate, @Nullable MergedAnnotationSelector<A> selector
   ) {
      MergedAnnotation<A> result = this.find(annotationType, predicate, selector);
      return result != null ? result : MergedAnnotation.missing();
   }

   private <A extends Annotation> @Nullable MergedAnnotation<A> find(
      Object requiredType, @Nullable Predicate<? super MergedAnnotation<A>> predicate, @Nullable MergedAnnotationSelector<A> selector
   ) {
      if (selector == null) {
         selector = MergedAnnotationSelectors.nearest();
      }

      MergedAnnotation<A> result = null;

      for (int i = 0; i < this.annotations.length; i++) {
         MergedAnnotation<?> root = this.annotations[i];
         if (root != null) {
            AnnotationTypeMappings mappings = this.mappings[i];

            for (int mappingIndex = 0; mappingIndex < mappings.size(); mappingIndex++) {
               AnnotationTypeMapping mapping = mappings.get(mappingIndex);
               if (isMappingForType(mapping, requiredType)) {
                  MergedAnnotation<A> candidate = mappingIndex == 0
                     ? root
                     : TypeMappedAnnotation.createIfPossible(mapping, root, IntrospectionFailureLogger.INFO);
                  if (candidate != null && (predicate == null || predicate.test(candidate))) {
                     if (selector.isBestCandidate(candidate)) {
                        return candidate;
                     }

                     result = result != null ? selector.select(result, candidate) : candidate;
                  }
               }
            }
         }
      }

      return result;
   }

   @Override
   public <A extends Annotation> Stream<MergedAnnotation<A>> stream(Class<A> annotationType) {
      return StreamSupport.stream(this.spliterator(annotationType), false);
   }

   @Override
   public <A extends Annotation> Stream<MergedAnnotation<A>> stream(String annotationType) {
      return StreamSupport.stream(this.spliterator(annotationType), false);
   }

   @Override
   public Stream<MergedAnnotation<Annotation>> stream() {
      return StreamSupport.stream(this.spliterator(), false);
   }

   private static boolean isMappingForType(AnnotationTypeMapping mapping, @Nullable Object requiredType) {
      if (requiredType == null) {
         return true;
      }

      Class<? extends Annotation> actualType = mapping.getAnnotationType();
      return actualType == requiredType || actualType.getName().equals(requiredType);
   }

   static MergedAnnotations of(Collection<MergedAnnotation<?>> annotations) {
      Assert.notNull(annotations, "Annotations must not be null");
      return annotations.isEmpty() ? TypeMappedAnnotations.NONE : new MergedAnnotationsCollection(annotations);
   }

   private class AnnotationsSpliterator<A extends Annotation> implements Spliterator<MergedAnnotation<A>> {
      private final @Nullable Object requiredType;
      private final int[] mappingCursors = new int[MergedAnnotationsCollection.this.annotations.length];

      public AnnotationsSpliterator(@Nullable Object requiredType) {
         this.requiredType = requiredType;
      }

      @Override
      public boolean tryAdvance(Consumer<? super MergedAnnotation<A>> action) {
         int lowestDistance = Integer.MAX_VALUE;
         int annotationResult = -1;

         for (int annotationIndex = 0; annotationIndex < MergedAnnotationsCollection.this.annotations.length; annotationIndex++) {
            AnnotationTypeMapping mapping = this.getNextSuitableMapping(annotationIndex);
            if (mapping != null && mapping.getDistance() < lowestDistance) {
               annotationResult = annotationIndex;
               lowestDistance = mapping.getDistance();
            }

            if (lowestDistance == 0) {
               break;
            }
         }

         if (annotationResult != -1) {
            MergedAnnotation<A> mergedAnnotation = this.createMergedAnnotationIfPossible(annotationResult, this.mappingCursors[annotationResult]);
            this.mappingCursors[annotationResult]++;
            if (mergedAnnotation == null) {
               return this.tryAdvance(action);
            }

            action.accept(mergedAnnotation);
            return true;
         } else {
            return false;
         }
      }

      private @Nullable AnnotationTypeMapping getNextSuitableMapping(int annotationIndex) {
         AnnotationTypeMapping mapping;
         do {
            mapping = this.getMapping(annotationIndex, this.mappingCursors[annotationIndex]);
            if (mapping != null && MergedAnnotationsCollection.isMappingForType(mapping, this.requiredType)) {
               return mapping;
            }

            this.mappingCursors[annotationIndex]++;
         } while (mapping != null);

         return null;
      }

      private @Nullable AnnotationTypeMapping getMapping(int annotationIndex, int mappingIndex) {
         AnnotationTypeMappings mappings = MergedAnnotationsCollection.this.mappings[annotationIndex];
         return mappingIndex < mappings.size() ? mappings.get(mappingIndex) : null;
      }

      private @Nullable MergedAnnotation<A> createMergedAnnotationIfPossible(int annotationIndex, int mappingIndex) {
         MergedAnnotation<?> root = MergedAnnotationsCollection.this.annotations[annotationIndex];
         if (mappingIndex == 0) {
            return (MergedAnnotation<A>)root;
         }

         IntrospectionFailureLogger logger = this.requiredType != null ? IntrospectionFailureLogger.INFO : IntrospectionFailureLogger.DEBUG;
         return TypeMappedAnnotation.createIfPossible(MergedAnnotationsCollection.this.mappings[annotationIndex].get(mappingIndex), root, logger);
      }

      @Override
      public @Nullable Spliterator<MergedAnnotation<A>> trySplit() {
         return null;
      }

      @Override
      public long estimateSize() {
         int size = 0;

         for (int i = 0; i < MergedAnnotationsCollection.this.annotations.length; i++) {
            AnnotationTypeMappings mappings = MergedAnnotationsCollection.this.mappings[i];
            int numberOfMappings = mappings.size();
            numberOfMappings -= Math.min(this.mappingCursors[i], mappings.size());
            size += numberOfMappings;
         }

         return size;
      }

      @Override
      public int characteristics() {
         return 1280;
      }
   }
}
