package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;
import org.jspecify.annotations.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public abstract class MergedAnnotationCollectors {
   private static final Characteristics[] NO_CHARACTERISTICS = new Characteristics[0];
   private static final Characteristics[] IDENTITY_FINISH_CHARACTERISTICS = new Characteristics[]{Characteristics.IDENTITY_FINISH};

   private MergedAnnotationCollectors() {
   }

   public static <A extends Annotation> Collector<MergedAnnotation<A>, ?, Set<A>> toAnnotationSet() {
      return Collector.of(LinkedHashSet::new, (set, annotation) -> set.add(annotation.synthesize()), MergedAnnotationCollectors::combiner);
   }

   public static <A extends Annotation> Collector<MergedAnnotation<A>, ?, Annotation[]> toAnnotationArray() {
      return toAnnotationArray(Annotation[]::new);
   }

   public static <R extends Annotation, A extends R> Collector<MergedAnnotation<A>, ?, R[]> toAnnotationArray(IntFunction<R[]> generator) {
      return Collector.of(
         ArrayList::new,
         (list, annotation) -> list.add(annotation.synthesize()),
         MergedAnnotationCollectors::combiner,
         list -> list.toArray((Annotation[])generator.apply(list.size()))
      );
   }

   public static <A extends Annotation> Collector<MergedAnnotation<A>, ? extends @Nullable Object, @Nullable MultiValueMap<String, @Nullable Object>> toMultiValueMap(
      MergedAnnotation.Adapt... adaptations
   ) {
      return toMultiValueMap(t -> t, adaptations);
   }

   public static <A extends Annotation> Collector<MergedAnnotation<A>, ? extends @Nullable Object, @Nullable MultiValueMap<String, @Nullable Object>> toMultiValueMap(
      Function<MultiValueMap<String, @Nullable @Nullable Object>, @Nullable MultiValueMap<String, Object>> finisher, MergedAnnotation.Adapt... adaptations
   ) {
      Characteristics[] characteristics = isSameInstance(finisher, Function.identity()) ? IDENTITY_FINISH_CHARACTERISTICS : NO_CHARACTERISTICS;
      return Collector.of(
         LinkedMultiValueMap::new,
         (map, annotation) -> annotation.asMap(adaptations).forEach(map::add),
         MergedAnnotationCollectors::combiner,
         finisher,
         characteristics
      );
   }

   private static boolean isSameInstance(Object instance, Object candidate) {
      return instance == candidate;
   }

   private static <E, C extends Collection<E>> C combiner(C collection, C additions) {
      collection.addAll(additions);
      return collection;
   }

   private static <K, V> MultiValueMap<K, @Nullable V> combiner(MultiValueMap<K, @Nullable V> map, MultiValueMap<K, @Nullable V> additions) {
      map.addAll(additions);
      return map;
   }
}
