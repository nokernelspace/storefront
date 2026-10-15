package org.springframework.aot.hint.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class ReflectiveRuntimeHintsRegistrar {
   private final Map<Class<? extends ReflectiveProcessor>, ReflectiveProcessor> processors = new HashMap<>();

   public void registerRuntimeHints(RuntimeHints runtimeHints, Class<?>... types) {
      Set<ReflectiveRuntimeHintsRegistrar.Entry> entries = new HashSet<>();

      for (Class<?> type : types) {
         this.processType(entries, type);

         for (Class<?> implementedInterface : ClassUtils.getAllInterfacesForClass(type)) {
            this.processType(entries, implementedInterface);
         }
      }

      entries.forEach(entry -> {
         AnnotatedElement element = entry.element();
         entry.processor().registerReflectionHints(runtimeHints.reflection(), element);
      });
   }

   public boolean isCandidate(Class<?> type) {
      if (this.isReflective(type)) {
         return true;
      }

      AtomicBoolean candidate = new AtomicBoolean(false);
      this.doWithReflectiveConstructors(type, constructor -> candidate.set(true));
      if (!candidate.get()) {
         ReflectionUtils.doWithFields(type, field -> candidate.set(true), this::isReflective);
      }

      if (!candidate.get()) {
         ReflectionUtils.doWithMethods(type, method -> candidate.set(true), this::isReflective);
      }

      return candidate.get();
   }

   private void processType(Set<ReflectiveRuntimeHintsRegistrar.Entry> entries, Class<?> typeToProcess) {
      if (this.isReflective(typeToProcess)) {
         entries.add(this.createEntry(typeToProcess));
      }

      this.doWithReflectiveConstructors(typeToProcess, constructor -> entries.add(this.createEntry(constructor)));
      ReflectionUtils.doWithFields(typeToProcess, field -> entries.add(this.createEntry(field)), this::isReflective);
      ReflectionUtils.doWithMethods(typeToProcess, method -> entries.add(this.createEntry(method)), this::isReflective);
   }

   private void doWithReflectiveConstructors(Class<?> typeToProcess, Consumer<Constructor<?>> consumer) {
      for (Constructor<?> constructor : typeToProcess.getDeclaredConstructors()) {
         if (this.isReflective(constructor)) {
            consumer.accept(constructor);
         }
      }
   }

   private boolean isReflective(AnnotatedElement element) {
      return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).isPresent(Reflective.class);
   }

   private ReflectiveRuntimeHintsRegistrar.Entry createEntry(AnnotatedElement element) {
      List<ReflectiveProcessor> processors = MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
         .stream(Reflective.class)
         .map(annotation -> annotation.getClassArray("value"))
         .flatMap(Arrays::stream)
         .distinct()
         .map(type -> (Class)type)
         .map(processorClass -> this.processors.computeIfAbsent((Class<? extends ReflectiveProcessor>)processorClass, this::instantiateClass))
         .toList();
      ReflectiveProcessor processorToUse = processors.size() == 1
         ? processors.get(0)
         : new ReflectiveRuntimeHintsRegistrar.DelegatingReflectiveProcessor(processors);
      return new ReflectiveRuntimeHintsRegistrar.Entry(element, processorToUse);
   }

   private ReflectiveProcessor instantiateClass(Class<? extends ReflectiveProcessor> type) {
      try {
         return ReflectionUtils.accessibleConstructor(type).newInstance();
      } catch (Exception ex) {
         throw new IllegalStateException("Failed to instantiate " + type, ex);
      }
   }

   private static class DelegatingReflectiveProcessor implements ReflectiveProcessor {
      private final Iterable<ReflectiveProcessor> processors;

      DelegatingReflectiveProcessor(Iterable<ReflectiveProcessor> processors) {
         this.processors = processors;
      }

      @Override
      public void registerReflectionHints(ReflectionHints hints, AnnotatedElement element) {
         this.processors.forEach(processor -> processor.registerReflectionHints(hints, element));
      }
   }

   private record Entry(AnnotatedElement element, ReflectiveProcessor processor) {
   }
}
