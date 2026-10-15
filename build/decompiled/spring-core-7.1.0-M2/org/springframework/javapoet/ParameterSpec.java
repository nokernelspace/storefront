package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

public final class ParameterSpec {
   private final String name;
   private final List<AnnotationSpec> annotations;
   private final Set<Modifier> modifiers;
   private final TypeName type;
   private final CodeBlock javadoc;

   private ParameterSpec(ParameterSpec.Builder builder) {
      this.name = Util.checkNotNull(builder.name, "name == null");
      this.annotations = Util.immutableList(builder.annotations);
      this.modifiers = Util.immutableSet(builder.modifiers);
      this.type = Util.checkNotNull(builder.type, "type == null");
      this.javadoc = builder.javadoc.build();
   }

   public String name() {
      return this.name;
   }

   public List<AnnotationSpec> annotations() {
      return this.annotations;
   }

   public Set<Modifier> modifiers() {
      return this.modifiers;
   }

   public TypeName type() {
      return this.type;
   }

   public CodeBlock javadoc() {
      return this.javadoc;
   }

   void emit(CodeWriter codeWriter, boolean varargs) throws IOException {
      codeWriter.emitAnnotations(this.annotations, true);
      codeWriter.emitModifiers(this.modifiers);
      if (varargs) {
         TypeName.asArray(this.type).emit(codeWriter, true);
      } else {
         this.type.emit(codeWriter);
      }

      codeWriter.emit(" $L", this.name);
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
         this.emit(codeWriter, false);
         return out.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static ParameterSpec get(VariableElement element) {
      Util.checkArgument(element.getKind().equals(ElementKind.PARAMETER), "element is not a parameter");
      TypeName type = TypeName.get(element.asType());
      String name = element.getSimpleName().toString();
      return builder(type, name).addModifiers(element.getModifiers()).build();
   }

   static List<ParameterSpec> parametersOf(ExecutableElement method) {
      List<ParameterSpec> result = new ArrayList<>();

      for (VariableElement parameter : method.getParameters()) {
         result.add(get(parameter));
      }

      return result;
   }

   private static boolean isValidParameterName(String name) {
      return name.endsWith(".this")
         ? SourceVersion.isIdentifier(name.substring(0, name.length() - ".this".length()))
         : name.equals("this") || SourceVersion.isName(name);
   }

   public static ParameterSpec.Builder builder(TypeName type, String name, Modifier... modifiers) {
      Util.checkNotNull(type, "type == null");
      Util.checkArgument(isValidParameterName(name), "not a valid name: %s", name);
      return new ParameterSpec.Builder(type, name).addModifiers(modifiers);
   }

   public static ParameterSpec.Builder builder(Type type, String name, Modifier... modifiers) {
      return builder(TypeName.get(type), name, modifiers);
   }

   public ParameterSpec.Builder toBuilder() {
      return this.toBuilder(this.type, this.name);
   }

   ParameterSpec.Builder toBuilder(TypeName type, String name) {
      ParameterSpec.Builder builder = new ParameterSpec.Builder(type, name);
      builder.annotations.addAll(this.annotations);
      builder.modifiers.addAll(this.modifiers);
      return builder;
   }

   public static final class Builder {
      private final TypeName type;
      private final String name;
      private final CodeBlock.Builder javadoc = CodeBlock.builder();
      private final List<AnnotationSpec> annotations = new ArrayList<>();
      private final List<Modifier> modifiers = new ArrayList<>();

      private Builder(TypeName type, String name) {
         this.type = type;
         this.name = name;
      }

      public ParameterSpec.Builder addJavadoc(String format, Object... args) {
         this.javadoc.add(format, args);
         return this;
      }

      public ParameterSpec.Builder addJavadoc(CodeBlock block) {
         this.javadoc.add(block);
         return this;
      }

      public ParameterSpec.Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
         Util.checkArgument(annotationSpecs != null, "annotationSpecs == null");

         for (AnnotationSpec annotationSpec : annotationSpecs) {
            this.annotations.add(annotationSpec);
         }

         return this;
      }

      public ParameterSpec.Builder addAnnotation(AnnotationSpec annotationSpec) {
         this.annotations.add(annotationSpec);
         return this;
      }

      public ParameterSpec.Builder addAnnotation(ClassName annotation) {
         this.annotations.add(AnnotationSpec.builder(annotation).build());
         return this;
      }

      public ParameterSpec.Builder addAnnotation(Class<?> annotation) {
         return this.addAnnotation(ClassName.get(annotation));
      }

      public ParameterSpec.Builder addModifiers(Modifier... modifiers) {
         Collections.addAll(this.modifiers, modifiers);
         return this;
      }

      public ParameterSpec.Builder addModifiers(Iterable<Modifier> modifiers) {
         Util.checkNotNull(modifiers, "modifiers == null");

         for (Modifier modifier : modifiers) {
            if (!modifier.equals(Modifier.FINAL)) {
               throw new IllegalStateException("unexpected parameter modifier: " + modifier);
            }

            this.modifiers.add(modifier);
         }

         return this;
      }

      public ParameterSpec build() {
         return new ParameterSpec(this);
      }
   }
}
