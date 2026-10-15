package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;

public final class FieldSpec {
   private final TypeName type;
   private final String name;
   private final CodeBlock javadoc;
   private final List<AnnotationSpec> annotations;
   private final Set<Modifier> modifiers;
   private final CodeBlock initializer;

   private FieldSpec(FieldSpec.Builder builder) {
      this.type = Util.checkNotNull(builder.type, "type == null");
      this.name = Util.checkNotNull(builder.name, "name == null");
      this.javadoc = builder.javadoc.build();
      this.annotations = Util.immutableList(builder.annotations);
      this.modifiers = Util.immutableSet(builder.modifiers);
      this.initializer = builder.initializer == null ? CodeBlock.builder().build() : builder.initializer;
   }

   public TypeName type() {
      return this.type;
   }

   public String name() {
      return this.name;
   }

   public CodeBlock javadoc() {
      return this.javadoc;
   }

   public List<AnnotationSpec> annotations() {
      return this.annotations;
   }

   public Set<Modifier> modifiers() {
      return this.modifiers;
   }

   public CodeBlock initializer() {
      return this.initializer;
   }

   void emit(CodeWriter codeWriter, Set<Modifier> implicitModifiers) throws IOException {
      codeWriter.emitJavadoc(this.javadoc);
      codeWriter.emitAnnotations(this.annotations, false);
      codeWriter.emitModifiers(this.modifiers, implicitModifiers);
      codeWriter.emit("$T $L", this.type, this.name);
      if (!this.initializer.isEmpty()) {
         codeWriter.emit(" = ");
         codeWriter.emit(this.initializer);
      }

      codeWriter.emit(";\n");
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o == null) {
         return false;
      } else {
         return this.getClass() != o.getClass() ? false : this.toString().equals(o.toString());
      }
   }

   @Override
   public int hashCode() {
      return this.toString().hashCode();
   }

   @Override
   public String toString() {
      StringBuilder out = new StringBuilder();

      try {
         CodeWriter codeWriter = new CodeWriter(out);
         this.emit(codeWriter, Collections.emptySet());
         return out.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static FieldSpec.Builder builder(TypeName type, String name, Modifier... modifiers) {
      Util.checkNotNull(type, "type == null");
      Util.checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
      return new FieldSpec.Builder(type, name).addModifiers(modifiers);
   }

   public static FieldSpec.Builder builder(Type type, String name, Modifier... modifiers) {
      return builder(TypeName.get(type), name, modifiers);
   }

   public FieldSpec.Builder toBuilder() {
      FieldSpec.Builder builder = new FieldSpec.Builder(this.type, this.name);
      builder.javadoc.add(this.javadoc);
      builder.annotations.addAll(this.annotations);
      builder.modifiers.addAll(this.modifiers);
      builder.initializer = this.initializer.isEmpty() ? null : this.initializer;
      return builder;
   }

   public static final class Builder {
      private final TypeName type;
      private final String name;
      private final CodeBlock.Builder javadoc = CodeBlock.builder();
      private CodeBlock initializer = null;
      private final List<AnnotationSpec> annotations = new ArrayList<>();
      private final List<Modifier> modifiers = new ArrayList<>();

      private Builder(TypeName type, String name) {
         this.type = type;
         this.name = name;
      }

      public FieldSpec.Builder addJavadoc(String format, Object... args) {
         this.javadoc.add(format, args);
         return this;
      }

      public FieldSpec.Builder addJavadoc(CodeBlock block) {
         this.javadoc.add(block);
         return this;
      }

      public FieldSpec.Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
         Util.checkArgument(annotationSpecs != null, "annotationSpecs == null");

         for (AnnotationSpec annotationSpec : annotationSpecs) {
            this.annotations.add(annotationSpec);
         }

         return this;
      }

      public FieldSpec.Builder addAnnotation(AnnotationSpec annotationSpec) {
         this.annotations.add(annotationSpec);
         return this;
      }

      public FieldSpec.Builder addAnnotation(ClassName annotation) {
         this.annotations.add(AnnotationSpec.builder(annotation).build());
         return this;
      }

      public FieldSpec.Builder addAnnotation(Class<?> annotation) {
         return this.addAnnotation(ClassName.get(annotation));
      }

      public FieldSpec.Builder addModifiers(Modifier... modifiers) {
         Collections.addAll(this.modifiers, modifiers);
         return this;
      }

      public FieldSpec.Builder initializer(String format, Object... args) {
         return this.initializer(CodeBlock.of(format, args));
      }

      public FieldSpec.Builder initializer(CodeBlock codeBlock) {
         Util.checkState(this.initializer == null, "initializer was already set");
         this.initializer = Util.checkNotNull(codeBlock, "codeBlock == null");
         return this;
      }

      public FieldSpec build() {
         return new FieldSpec(this);
      }
   }
}
