package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

final class TypeMappedAnnotations implements MergedAnnotations {
   static final MergedAnnotations NONE = new TypeMappedAnnotations(null, new Annotation[0], RepeatableContainers.none(), AnnotationFilter.ALL);
   private final @Nullable Object source;
   private final @Nullable AnnotatedElement element;
   private final MergedAnnotations.@Nullable SearchStrategy searchStrategy;
   private final Predicate<Class<?>> searchEnclosingClass;
   private final Annotation @Nullable [] annotations;
   private final RepeatableContainers repeatableContainers;
   private final AnnotationFilter annotationFilter;
   private volatile @Nullable List<TypeMappedAnnotations.Aggregate> aggregates;

   private TypeMappedAnnotations(
      AnnotatedElement element,
      MergedAnnotations.SearchStrategy searchStrategy,
      Predicate<Class<?>> searchEnclosingClass,
      RepeatableContainers repeatableContainers,
      AnnotationFilter annotationFilter
   ) {
      this.source = element;
      this.element = element;
      this.searchStrategy = searchStrategy;
      this.searchEnclosingClass = searchEnclosingClass;
      this.annotations = null;
      this.repeatableContainers = repeatableContainers;
      this.annotationFilter = annotationFilter;
   }

   private TypeMappedAnnotations(
      @Nullable Object source, Annotation[] annotations, RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter
   ) {
      this.source = source;
      this.element = null;
      this.searchStrategy = null;
      this.searchEnclosingClass = MergedAnnotations.Search.never;
      this.annotations = annotations;
      this.repeatableContainers = repeatableContainers;
      this.annotationFilter = annotationFilter;
   }

   @Override
   public <A extends Annotation> boolean isPresent(Class<A> annotationType) {
      return this.annotationFilter.matches(annotationType)
         ? false
         : Boolean.TRUE.equals(this.scan(annotationType, TypeMappedAnnotations.IsPresent.get(this.repeatableContainers, this.annotationFilter, false)));
   }

   @Override
   public boolean isPresent(String annotationType) {
      return this.annotationFilter.matches(annotationType)
         ? false
         : Boolean.TRUE.equals(this.scan(annotationType, TypeMappedAnnotations.IsPresent.get(this.repeatableContainers, this.annotationFilter, false)));
   }

   @Override
   public <A extends Annotation> boolean isDirectlyPresent(Class<A> annotationType) {
      return this.annotationFilter.matches(annotationType)
         ? false
         : Boolean.TRUE.equals(this.scan(annotationType, TypeMappedAnnotations.IsPresent.get(this.repeatableContainers, this.annotationFilter, true)));
   }

   @Override
   public boolean isDirectlyPresent(String annotationType) {
      return this.annotationFilter.matches(annotationType)
         ? false
         : Boolean.TRUE.equals(this.scan(annotationType, TypeMappedAnnotations.IsPresent.get(this.repeatableContainers, this.annotationFilter, true)));
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
      if (this.annotationFilter.matches(annotationType)) {
         return MergedAnnotation.missing();
      }

      MergedAnnotation<A> result = this.scan(annotationType, new TypeMappedAnnotations.MergedAnnotationFinder<>(annotationType, predicate, selector));
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
      if (this.annotationFilter.matches(annotationType)) {
         return MergedAnnotation.missing();
      }

      MergedAnnotation<A> result = this.scan(annotationType, new TypeMappedAnnotations.MergedAnnotationFinder<>(annotationType, predicate, selector));
      return result != null ? result : MergedAnnotation.missing();
   }

   @Override
   public <A extends Annotation> Stream<MergedAnnotation<A>> stream(Class<A> annotationType) {
      return this.annotationFilter == AnnotationFilter.ALL ? Stream.empty() : StreamSupport.stream(this.spliterator(annotationType), false);
   }

   @Override
   public <A extends Annotation> Stream<MergedAnnotation<A>> stream(String annotationType) {
      return this.annotationFilter == AnnotationFilter.ALL ? Stream.empty() : StreamSupport.stream(this.spliterator(annotationType), false);
   }

   @Override
   public Stream<MergedAnnotation<Annotation>> stream() {
      return this.annotationFilter == AnnotationFilter.ALL ? Stream.empty() : StreamSupport.stream(this.spliterator(), false);
   }

   @Override
   public Iterator<MergedAnnotation<Annotation>> iterator() {
      return this.annotationFilter == AnnotationFilter.ALL ? Collections.emptyIterator() : Spliterators.iterator(this.spliterator());
   }

   @Override
   public Spliterator<MergedAnnotation<Annotation>> spliterator() {
      return this.annotationFilter == AnnotationFilter.ALL ? Spliterators.emptySpliterator() : this.spliterator(null);
   }

   private <A extends Annotation> Spliterator<MergedAnnotation<A>> spliterator(@Nullable Object annotationType) {
      return new TypeMappedAnnotations.AggregatesSpliterator<>(annotationType, this.getAggregates());
   }

   private List<TypeMappedAnnotations.Aggregate> getAggregates() {
      List<TypeMappedAnnotations.Aggregate> aggregates = this.aggregates;
      if (aggregates == null) {
         aggregates = this.scan(this, new TypeMappedAnnotations.AggregatesCollector());
         if (aggregates == null || aggregates.isEmpty()) {
            aggregates = Collections.emptyList();
         }

         this.aggregates = aggregates;
      }

      return aggregates;
   }

   private <C, R> @Nullable R scan(C criteria, AnnotationsProcessor<C, R> processor) {
      if (this.annotations != null) {
         R result = processor.doWithAnnotations(criteria, 0, this.source, this.annotations);
         return processor.finish(result);
      } else {
         return this.element != null && this.searchStrategy != null
            ? AnnotationsScanner.scan(criteria, this.element, this.searchStrategy, this.searchEnclosingClass, processor)
            : null;
      }
   }

   static MergedAnnotations from(
      AnnotatedElement element,
      MergedAnnotations.SearchStrategy searchStrategy,
      Predicate<Class<?>> searchEnclosingClass,
      RepeatableContainers repeatableContainers,
      AnnotationFilter annotationFilter
   ) {
      return AnnotationsScanner.isKnownEmpty(element, searchStrategy, searchEnclosingClass)
         ? NONE
         : new TypeMappedAnnotations(element, searchStrategy, searchEnclosingClass, repeatableContainers, annotationFilter);
   }

   static MergedAnnotations from(
      @Nullable Object source, Annotation[] annotations, RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter
   ) {
      return annotations.length == 0 ? NONE : new TypeMappedAnnotations(source, annotations, repeatableContainers, annotationFilter);
   }

   private static boolean isMappingForType(AnnotationTypeMapping mapping, AnnotationFilter annotationFilter, @Nullable Object requiredType) {
      Class<? extends Annotation> actualType = mapping.getAnnotationType();
      return !annotationFilter.matches(actualType) && (requiredType == null || actualType == requiredType || actualType.getName().equals(requiredType));
   }

   private static class Aggregate {
      private final int aggregateIndex;
      private final @Nullable Object source;
      private final List<Annotation> annotations;
      private final AnnotationTypeMappings[] mappings;

      Aggregate(int aggregateIndex, @Nullable Object source, List<Annotation> annotations) {
         this.aggregateIndex = aggregateIndex;
         this.source = source;
         this.annotations = annotations;
         this.mappings = new AnnotationTypeMappings[annotations.size()];

         for (int i = 0; i < annotations.size(); i++) {
            this.mappings[i] = AnnotationTypeMappings.forAnnotationType(annotations.get(i).annotationType());
         }
      }

      int size() {
         return this.annotations.size();
      }

      @Nullable AnnotationTypeMapping getMapping(int annotationIndex, int mappingIndex) {
         AnnotationTypeMappings mappings = this.getMappings(annotationIndex);
         return mappingIndex < mappings.size() ? mappings.get(mappingIndex) : null;
      }

      AnnotationTypeMappings getMappings(int annotationIndex) {
         return this.mappings[annotationIndex];
      }

      <A extends Annotation> @Nullable MergedAnnotation<A> createMergedAnnotationIfPossible(
         int annotationIndex, int mappingIndex, IntrospectionFailureLogger logger
      ) {
         return TypeMappedAnnotation.createIfPossible(
            this.mappings[annotationIndex].get(mappingIndex), this.source, this.annotations.get(annotationIndex), this.aggregateIndex, logger
         );
      }
   }

   private class AggregatesCollector implements AnnotationsProcessor<Object, List<TypeMappedAnnotations.Aggregate>> {
      private final List<TypeMappedAnnotations.Aggregate> aggregates = new ArrayList<>();

      public @Nullable List<TypeMappedAnnotations.Aggregate> doWithAnnotations(
         Object criteria, int aggregateIndex, @Nullable Object source, @Nullable Annotation[] annotations
      ) {
         this.aggregates.add(this.createAggregate(aggregateIndex, source, annotations));
         return null;
      }

      private TypeMappedAnnotations.Aggregate createAggregate(int aggregateIndex, @Nullable Object source, @Nullable Annotation[] annotations) {
         List<Annotation> aggregateAnnotations = this.getAggregateAnnotations(annotations);
         return new TypeMappedAnnotations.Aggregate(aggregateIndex, source, aggregateAnnotations);
      }

      private List<Annotation> getAggregateAnnotations(@Nullable Annotation[] annotations) {
         List<Annotation> result = new ArrayList<>(annotations.length);
         this.addAggregateAnnotations(result, annotations);
         return result;
      }

      private void addAggregateAnnotations(List<Annotation> aggregateAnnotations, @Nullable Annotation[] annotations) {
         for (Annotation annotation : annotations) {
            if (annotation != null && !TypeMappedAnnotations.this.annotationFilter.matches(annotation)) {
               Annotation[] repeatedAnnotations = TypeMappedAnnotations.this.repeatableContainers.findRepeatedAnnotations(annotation);
               if (repeatedAnnotations != null) {
                  this.addAggregateAnnotations(aggregateAnnotations, repeatedAnnotations);
               } else {
                  aggregateAnnotations.add(annotation);
               }
            }
         }
      }

      public List<TypeMappedAnnotations.Aggregate> finish(@Nullable List<TypeMappedAnnotations.Aggregate> processResult) {
         return this.aggregates;
      }
   }

   private class AggregatesSpliterator<A extends Annotation> implements Spliterator<MergedAnnotation<A>> {
      private final @Nullable Object requiredType;
      private final List<TypeMappedAnnotations.Aggregate> aggregates;
      private int aggregateCursor;
      private int @Nullable [] mappingCursors;

      AggregatesSpliterator(@Nullable Object requiredType, List<TypeMappedAnnotations.Aggregate> aggregates) {
         this.requiredType = requiredType;
         this.aggregates = aggregates;
         this.aggregateCursor = 0;
      }

      @Override
      public boolean tryAdvance(Consumer<? super MergedAnnotation<A>> action) {
         while (this.aggregateCursor < this.aggregates.size()) {
            TypeMappedAnnotations.Aggregate aggregate = this.aggregates.get(this.aggregateCursor);
            if (this.tryAdvance(aggregate, action)) {
               return true;
            }

            this.aggregateCursor++;
            this.mappingCursors = null;
         }

         return false;
      }

      private boolean tryAdvance(TypeMappedAnnotations.Aggregate aggregate, Consumer<? super MergedAnnotation<A>> action) {
         if (this.mappingCursors == null) {
            this.mappingCursors = new int[aggregate.size()];
         }

         int lowestDistance = Integer.MAX_VALUE;
         int annotationResult = -1;

         for (int annotationIndex = 0; annotationIndex < aggregate.size(); annotationIndex++) {
            AnnotationTypeMapping mapping = this.getNextSuitableMapping(aggregate, annotationIndex);
            if (mapping != null && mapping.getDistance() < lowestDistance) {
               annotationResult = annotationIndex;
               lowestDistance = mapping.getDistance();
            }

            if (lowestDistance == 0) {
               break;
            }
         }

         if (annotationResult != -1) {
            MergedAnnotation<A> mergedAnnotation = aggregate.createMergedAnnotationIfPossible(
               annotationResult,
               this.mappingCursors[annotationResult],
               this.requiredType != null ? IntrospectionFailureLogger.INFO : IntrospectionFailureLogger.DEBUG
            );
            this.mappingCursors[annotationResult]++;
            if (mergedAnnotation == null) {
               return this.tryAdvance(aggregate, action);
            }

            action.accept(mergedAnnotation);
            return true;
         } else {
            return false;
         }
      }

      private @Nullable AnnotationTypeMapping getNextSuitableMapping(TypeMappedAnnotations.Aggregate aggregate, int annotationIndex) {
         int[] cursors = this.mappingCursors;
         AnnotationTypeMapping mapping;
         if (cursors != null) {
            do {
               mapping = aggregate.getMapping(annotationIndex, cursors[annotationIndex]);
               if (mapping != null && TypeMappedAnnotations.isMappingForType(mapping, TypeMappedAnnotations.this.annotationFilter, this.requiredType)) {
                  return mapping;
               }

               cursors[annotationIndex]++;
            } while (mapping != null);
         }

         return null;
      }

      @Override
      public @Nullable Spliterator<MergedAnnotation<A>> trySplit() {
         return null;
      }

      @Override
      public long estimateSize() {
         int size = 0;

         for (int aggregateIndex = this.aggregateCursor; aggregateIndex < this.aggregates.size(); aggregateIndex++) {
            TypeMappedAnnotations.Aggregate aggregate = this.aggregates.get(aggregateIndex);

            for (int annotationIndex = 0; annotationIndex < aggregate.size(); annotationIndex++) {
               AnnotationTypeMappings mappings = aggregate.getMappings(annotationIndex);
               int numberOfMappings = mappings.size();
               if (aggregateIndex == this.aggregateCursor && this.mappingCursors != null) {
                  numberOfMappings -= Math.min(this.mappingCursors[annotationIndex], mappings.size());
               }

               size += numberOfMappings;
            }
         }

         return size;
      }

      @Override
      public int characteristics() {
         return 1280;
      }
   }

   private static final class IsPresent implements AnnotationsProcessor<Object, Boolean> {
      private static final TypeMappedAnnotations.IsPresent[] SHARED = new TypeMappedAnnotations.IsPresent[4];
      private final RepeatableContainers repeatableContainers;
      private final AnnotationFilter annotationFilter;
      private final boolean directOnly;

      private IsPresent(RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter, boolean directOnly) {
         this.repeatableContainers = repeatableContainers;
         this.annotationFilter = annotationFilter;
         this.directOnly = directOnly;
      }

      public @Nullable Boolean doWithAnnotations(Object requiredType, int aggregateIndex, @Nullable Object source, @Nullable Annotation[] annotations) {
         for (Annotation annotation : annotations) {
            if (annotation != null) {
               Class<? extends Annotation> type = annotation.annotationType();
               if (type != null && !this.annotationFilter.matches(type)) {
                  if (type == requiredType || type.getName().equals(requiredType)) {
                     return Boolean.TRUE;
                  }

                  Annotation[] repeatedAnnotations = this.repeatableContainers.findRepeatedAnnotations(annotation);
                  if (repeatedAnnotations != null) {
                     Boolean result = this.doWithAnnotations(requiredType, aggregateIndex, source, repeatedAnnotations);
                     if (result != null) {
                        return result;
                     }
                  }

                  if (!this.directOnly) {
                     AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(type);

                     for (int i = 0; i < mappings.size(); i++) {
                        AnnotationTypeMapping mapping = mappings.get(i);
                        if (TypeMappedAnnotations.isMappingForType(mapping, this.annotationFilter, requiredType)) {
                           return Boolean.TRUE;
                        }
                     }
                  }
               }
            }
         }

         return null;
      }

      static TypeMappedAnnotations.IsPresent get(RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter, boolean directOnly) {
         if (annotationFilter == AnnotationFilter.PLAIN) {
            if (repeatableContainers == RepeatableContainers.none()) {
               return SHARED[directOnly ? 0 : 1];
            }

            if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
               return SHARED[directOnly ? 2 : 3];
            }
         }

         return new TypeMappedAnnotations.IsPresent(repeatableContainers, annotationFilter, directOnly);
      }

      static {
         SHARED[0] = new TypeMappedAnnotations.IsPresent(RepeatableContainers.none(), AnnotationFilter.PLAIN, true);
         SHARED[1] = new TypeMappedAnnotations.IsPresent(RepeatableContainers.none(), AnnotationFilter.PLAIN, false);
         SHARED[2] = new TypeMappedAnnotations.IsPresent(RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, true);
         SHARED[3] = new TypeMappedAnnotations.IsPresent(RepeatableContainers.standardRepeatables(), AnnotationFilter.PLAIN, false);
      }
   }

   private class MergedAnnotationFinder<A extends Annotation> implements AnnotationsProcessor<Object, MergedAnnotation<A>> {
      private final Object requiredType;
      private final @Nullable Predicate<? super MergedAnnotation<A>> predicate;
      private final MergedAnnotationSelector<A> selector;
      private @Nullable MergedAnnotation<A> result;

      MergedAnnotationFinder(Object requiredType, @Nullable Predicate<? super MergedAnnotation<A>> predicate, @Nullable MergedAnnotationSelector<A> selector) {
         this.requiredType = requiredType;
         this.predicate = predicate;
         this.selector = selector != null ? selector : MergedAnnotationSelectors.nearest();
      }

      public @Nullable MergedAnnotation<A> doWithAggregate(Object context, int aggregateIndex) {
         return this.result;
      }

      public @Nullable MergedAnnotation<A> doWithAnnotations(Object type, int aggregateIndex, @Nullable Object source, @Nullable Annotation[] annotations) {
         for (Annotation annotation : annotations) {
            if (annotation != null && !TypeMappedAnnotations.this.annotationFilter.matches(annotation)) {
               MergedAnnotation<A> result = this.process(type, aggregateIndex, source, annotation);
               if (result != null) {
                  return result;
               }
            }
         }

         return null;
      }

      private @Nullable MergedAnnotation<A> process(Object type, int aggregateIndex, @Nullable Object source, Annotation annotation) {
         Annotation[] repeatedAnnotations = TypeMappedAnnotations.this.repeatableContainers.findRepeatedAnnotations(annotation);
         if (repeatedAnnotations != null) {
            MergedAnnotation<A> result = this.doWithAnnotations(type, aggregateIndex, source, repeatedAnnotations);
            if (result != null) {
               return result;
            }
         }

         AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(
            annotation.annotationType(), TypeMappedAnnotations.this.repeatableContainers, TypeMappedAnnotations.this.annotationFilter
         );

         for (int i = 0; i < mappings.size(); i++) {
            AnnotationTypeMapping mapping = mappings.get(i);
            if (TypeMappedAnnotations.isMappingForType(mapping, TypeMappedAnnotations.this.annotationFilter, this.requiredType)) {
               MergedAnnotation<A> candidate = TypeMappedAnnotation.createIfPossible(
                  mapping, source, annotation, aggregateIndex, IntrospectionFailureLogger.INFO
               );
               if (candidate != null && (this.predicate == null || this.predicate.test(candidate))) {
                  if (this.selector.isBestCandidate(candidate)) {
                     return candidate;
                  }

                  this.updateLastResult(candidate);
               }
            }
         }

         return null;
      }

      private void updateLastResult(MergedAnnotation<A> candidate) {
         MergedAnnotation<A> lastResult = this.result;
         this.result = lastResult != null ? this.selector.select(lastResult, candidate) : candidate;
      }

      public @Nullable MergedAnnotation<A> finish(@Nullable MergedAnnotation<A> result) {
         return result != null ? result : this.result;
      }
   }
}
