package org.springframework.beans.factory.aot;

import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.AnnotationSpec;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.AnnotationSpec.Builder;
import org.springframework.util.ClassUtils;

public class CodeWarnings {
   private final Set<String> warnings = new LinkedHashSet<>();

   public void register(String warning) {
      this.warnings.add(warning);
   }

   public CodeWarnings detectDeprecation(AnnotatedElement... elements) {
      for (AnnotatedElement element : elements) {
         this.registerDeprecationIfNecessary(element);
      }

      return this;
   }

   public CodeWarnings detectDeprecation(Stream<AnnotatedElement> elements) {
      elements.forEach(element -> this.register(element.getAnnotation(Deprecated.class)));
      return this;
   }

   public CodeWarnings detectDeprecation(ResolvableType resolvableType) {
      if (ResolvableType.NONE.equals(resolvableType)) {
         return this;
      }

      Class<?> type = ClassUtils.getUserClass(resolvableType.toClass());
      this.detectDeprecation(type);
      if (resolvableType.hasGenerics() && !resolvableType.hasUnresolvableGenerics()) {
         for (ResolvableType generic : resolvableType.getGenerics()) {
            this.detectDeprecation(generic);
         }
      }

      return this;
   }

   public void suppress(org.springframework.javapoet.MethodSpec.Builder method) {
      this.suppress(annotationBuilder -> method.addAnnotation(annotationBuilder.build()));
   }

   public void suppress(org.springframework.javapoet.TypeSpec.Builder type) {
      this.suppress(annotationBuilder -> type.addAnnotation(annotationBuilder.build()));
   }

   protected void suppress(Consumer<Builder> annotationSpec) {
      if (!this.warnings.isEmpty()) {
         Builder annotation = AnnotationSpec.builder(SuppressWarnings.class).addMember("value", this.generateValueCode());
         annotationSpec.accept(annotation);
      }
   }

   protected Set<String> getWarnings() {
      return Collections.unmodifiableSet(this.warnings);
   }

   private void registerDeprecationIfNecessary(@Nullable AnnotatedElement element) {
      if (element != null) {
         this.register(element.getAnnotation(Deprecated.class));
         if (element instanceof Class<?> type) {
            this.registerDeprecationIfNecessary(type.getEnclosingClass());
         }
      }
   }

   private void register(@Nullable Deprecated annotation) {
      if (annotation != null) {
         if (annotation.forRemoval()) {
            this.register("removal");
         } else {
            this.register("deprecation");
         }
      }
   }

   private CodeBlock generateValueCode() {
      if (this.warnings.size() == 1) {
         return CodeBlock.of("$S", new Object[]{this.warnings.iterator().next()});
      }

      CodeBlock values = CodeBlock.join(this.warnings.stream().map(warning -> CodeBlock.of("$S", new Object[]{warning})).toList(), ", ");
      return CodeBlock.of("{ $L }", new Object[]{values});
   }

   @Override
   public String toString() {
      return CodeWarnings.class.getSimpleName() + this.warnings;
   }
}
