package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;

public final class MethodSpec {
   private static final String CONSTRUCTOR = "<init>";
   private final String name;
   private final CodeBlock javadoc;
   private final List<AnnotationSpec> annotations;
   private final Set<Modifier> modifiers;
   private final List<TypeVariableName> typeVariables;
   private final TypeName returnType;
   private final List<ParameterSpec> parameters;
   private final boolean varargs;
   private final List<TypeName> exceptions;
   private final CodeBlock code;
   private final CodeBlock defaultValue;
   private final boolean compactConstructor;

   private MethodSpec(MethodSpec.Builder builder) {
      CodeBlock code = builder.code.build();
      Util.checkArgument(code.isEmpty() || !builder.modifiers.contains(Modifier.ABSTRACT), "abstract method %s cannot have code", builder.name);
      Util.checkArgument(
         !builder.varargs || this.lastParameterIsArray(builder.parameters), "last parameter of varargs method %s must be an array", builder.name
      );
      this.name = Util.checkNotNull(builder.name, "name == null");
      this.javadoc = builder.javadoc.build();
      this.annotations = Util.immutableList(builder.annotations);
      this.modifiers = Util.immutableSet(builder.modifiers);
      this.typeVariables = Util.immutableList(builder.typeVariables);
      this.returnType = builder.returnType;
      this.parameters = Util.immutableList(builder.parameters);
      this.varargs = builder.varargs;
      this.exceptions = Util.immutableList(builder.exceptions);
      this.defaultValue = builder.defaultValue;
      this.code = code;
      this.compactConstructor = builder.compactConstructor;
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

   public List<TypeVariableName> typeVariables() {
      return this.typeVariables;
   }

   public TypeName returnType() {
      return this.returnType;
   }

   public List<ParameterSpec> parameters() {
      return this.parameters;
   }

   public boolean varargs() {
      return this.varargs;
   }

   public List<TypeName> exceptions() {
      return this.exceptions;
   }

   public CodeBlock code() {
      return this.code;
   }

   public CodeBlock defaultValue() {
      return this.defaultValue;
   }

   public boolean isConstructor() {
      return this.name.equals("<init>");
   }

   private boolean lastParameterIsArray(List<ParameterSpec> parameters) {
      return !parameters.isEmpty() && TypeName.asArray(parameters.get(parameters.size() - 1).type()) != null;
   }

   void emit(CodeWriter codeWriter, String enclosingName, Set<Modifier> implicitModifiers) throws IOException {
      codeWriter.emitJavadocWithParameters(this.javadoc, this.parameters);
      codeWriter.emitAnnotations(this.annotations, false);
      codeWriter.emitModifiers(this.modifiers, implicitModifiers);
      if (!this.typeVariables.isEmpty()) {
         codeWriter.emitTypeVariables(this.typeVariables);
         codeWriter.emit(" ");
      }

      if (this.compactConstructor) {
         codeWriter.emit("$L", enclosingName);
      } else if (this.isConstructor()) {
         codeWriter.emit("$L", enclosingName);
         codeWriter.emitParameters(this.parameters, this.varargs);
      } else {
         codeWriter.emit("$T $L", this.returnType, this.name);
         codeWriter.emitParameters(this.parameters, this.varargs);
      }

      if (this.defaultValue != null && !this.defaultValue.isEmpty()) {
         codeWriter.emit(" default ");
         codeWriter.emit(this.defaultValue);
      }

      if (!this.exceptions.isEmpty()) {
         codeWriter.emitWrappingSpace().emit("throws");
         boolean firstException = true;

         for (TypeName exception : this.exceptions) {
            if (!firstException) {
               codeWriter.emit(",");
            }

            codeWriter.emitWrappingSpace().emit("$T", exception);
            firstException = false;
         }
      }

      if (this.modifiers.contains(Modifier.ABSTRACT)) {
         codeWriter.emit(";\n");
      } else if (this.modifiers.contains(Modifier.NATIVE)) {
         codeWriter.emit(this.code);
         codeWriter.emit(";\n");
      } else {
         codeWriter.emit(" {\n");
         codeWriter.indent();
         codeWriter.emit(this.code, true);
         codeWriter.unindent();
         codeWriter.emit("}\n");
      }

      codeWriter.popTypeVariables(this.typeVariables);
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
         this.emit(codeWriter, "Constructor", Collections.emptySet());
         return out.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static MethodSpec.Builder methodBuilder(String name) {
      return new MethodSpec.Builder(name, false);
   }

   public static MethodSpec.Builder constructorBuilder() {
      return new MethodSpec.Builder("<init>", false);
   }

   public static MethodSpec.Builder compactConstructorBuilder() {
      return new MethodSpec.Builder("<init>", true);
   }

   public static MethodSpec.Builder overriding(ExecutableElement method) {
      Util.checkNotNull(method, "method == null");
      Element enclosingClass = method.getEnclosingElement();
      if (enclosingClass.getModifiers().contains(Modifier.FINAL)) {
         throw new IllegalArgumentException("Cannot override method on final class " + enclosingClass);
      }

      Set<Modifier> modifiers = method.getModifiers();
      if (!modifiers.contains(Modifier.PRIVATE) && !modifiers.contains(Modifier.FINAL) && !modifiers.contains(Modifier.STATIC)) {
         String methodName = method.getSimpleName().toString();
         MethodSpec.Builder methodBuilder = methodBuilder(methodName);
         methodBuilder.addAnnotation(Override.class);
         modifiers = new LinkedHashSet<>(modifiers);
         modifiers.remove(Modifier.ABSTRACT);
         modifiers.remove(Modifier.DEFAULT);
         methodBuilder.addModifiers(modifiers);

         for (TypeParameterElement typeParameterElement : method.getTypeParameters()) {
            TypeVariable var = (TypeVariable)typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
         }

         methodBuilder.returns(TypeName.get(method.getReturnType()));
         methodBuilder.addParameters(ParameterSpec.parametersOf(method));
         methodBuilder.varargs(method.isVarArgs());

         for (TypeMirror thrownType : method.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrownType));
         }

         return methodBuilder;
      } else {
         throw new IllegalArgumentException("cannot override method with modifiers: " + modifiers);
      }
   }

   public static MethodSpec.Builder overriding(ExecutableElement method, DeclaredType enclosing, Types types) {
      ExecutableType executableType = (ExecutableType)types.asMemberOf(enclosing, method);
      List<? extends TypeVariable> resolvedTypeVariables = executableType.getTypeVariables();
      List<? extends TypeMirror> resolvedParameterTypes = executableType.getParameterTypes();
      List<? extends TypeMirror> resolvedThrownTypes = executableType.getThrownTypes();
      TypeMirror resolvedReturnType = executableType.getReturnType();
      MethodSpec.Builder builder = overriding(method);
      builder.returns(TypeName.get(resolvedReturnType));
      int i = 0;

      for (int size = builder.parameters.size(); i < size; i++) {
         ParameterSpec parameter = builder.parameters.get(i);
         TypeName type = TypeName.get(resolvedParameterTypes.get(i));
         builder.parameters.set(i, parameter.toBuilder(type, parameter.name()).build());
      }

      i = 0;

      for (int size = builder.typeVariables.size(); i < size; i++) {
         TypeVariable resolvedTypeVariable = resolvedTypeVariables.get(i);
         String name = resolvedTypeVariable.asElement().getSimpleName().toString();
         List<TypeName> bounds = new ArrayList<>();
         if (resolvedTypeVariable.getUpperBound().getKind() != TypeKind.NULL) {
            bounds.add(TypeName.get(resolvedTypeVariable.getUpperBound()));
         }

         builder.typeVariables.set(i, TypeVariableName.get(name, bounds.toArray(new TypeName[0])));
      }

      builder.exceptions.clear();

      for (TypeMirror resolvedThrownType : resolvedThrownTypes) {
         builder.addException(TypeName.get(resolvedThrownType));
      }

      return builder;
   }

   public MethodSpec.Builder toBuilder() {
      MethodSpec.Builder builder = new MethodSpec.Builder(this.name, this.compactConstructor);
      builder.javadoc.add(this.javadoc);
      builder.annotations.addAll(this.annotations);
      builder.modifiers.addAll(this.modifiers);
      builder.typeVariables.addAll(this.typeVariables);
      builder.returnType = this.returnType;
      builder.parameters.addAll(this.parameters);
      builder.exceptions.addAll(this.exceptions);
      builder.code.add(this.code);
      builder.varargs = this.varargs;
      builder.defaultValue = this.defaultValue;
      return builder;
   }

   public static final class Builder {
      private String name;
      private final CodeBlock.Builder javadoc = CodeBlock.builder();
      private TypeName returnType;
      private final Set<TypeName> exceptions = new LinkedHashSet<>();
      private final CodeBlock.Builder code = CodeBlock.builder();
      private boolean varargs;
      private CodeBlock defaultValue;
      private final List<TypeVariableName> typeVariables = new ArrayList<>();
      private final List<AnnotationSpec> annotations = new ArrayList<>();
      private final List<Modifier> modifiers = new ArrayList<>();
      private final List<ParameterSpec> parameters = new ArrayList<>();
      private final boolean compactConstructor;

      private Builder(String name, boolean compactConstructor) {
         this.setName(name);
         this.compactConstructor = compactConstructor;
      }

      public MethodSpec.Builder setName(String name) {
         Util.checkNotNull(name, "name == null");
         Util.checkArgument(name.equals("<init>") || SourceVersion.isName(name), "not a valid name: %s", name);
         this.name = name;
         this.returnType = name.equals("<init>") ? null : TypeName.VOID;
         return this;
      }

      public MethodSpec.Builder addJavadoc(String format, Object... args) {
         this.javadoc.add(format, args);
         return this;
      }

      public MethodSpec.Builder addJavadoc(CodeBlock block) {
         this.javadoc.add(block);
         return this;
      }

      public MethodSpec.Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
         Util.checkArgument(annotationSpecs != null, "annotationSpecs == null");

         for (AnnotationSpec annotationSpec : annotationSpecs) {
            this.annotations.add(annotationSpec);
         }

         return this;
      }

      public MethodSpec.Builder addAnnotation(AnnotationSpec annotationSpec) {
         this.annotations.add(annotationSpec);
         return this;
      }

      public MethodSpec.Builder addAnnotation(ClassName annotation) {
         this.annotations.add(AnnotationSpec.builder(annotation).build());
         return this;
      }

      public MethodSpec.Builder addAnnotation(Class<?> annotation) {
         return this.addAnnotation(ClassName.get(annotation));
      }

      public MethodSpec.Builder addModifiers(Modifier... modifiers) {
         Util.checkNotNull(modifiers, "modifiers == null");
         Collections.addAll(this.modifiers, modifiers);
         return this;
      }

      public MethodSpec.Builder addModifiers(Iterable<Modifier> modifiers) {
         Util.checkNotNull(modifiers, "modifiers == null");

         for (Modifier modifier : modifiers) {
            this.modifiers.add(modifier);
         }

         return this;
      }

      public MethodSpec.Builder addTypeVariables(Iterable<TypeVariableName> typeVariables) {
         Util.checkArgument(typeVariables != null, "typeVariables == null");

         for (TypeVariableName typeVariable : typeVariables) {
            this.typeVariables.add(typeVariable);
         }

         return this;
      }

      public MethodSpec.Builder addTypeVariable(TypeVariableName typeVariable) {
         this.typeVariables.add(typeVariable);
         return this;
      }

      public MethodSpec.Builder returns(TypeName returnType) {
         Util.checkState(!this.name.equals("<init>"), "constructor cannot have return type.");
         this.returnType = returnType;
         return this;
      }

      public MethodSpec.Builder returns(Type returnType) {
         return this.returns(TypeName.get(returnType));
      }

      public MethodSpec.Builder addParameters(Iterable<ParameterSpec> parameterSpecs) {
         Util.checkArgument(parameterSpecs != null, "parameterSpecs == null");

         for (ParameterSpec parameterSpec : parameterSpecs) {
            this.parameters.add(parameterSpec);
         }

         return this;
      }

      public MethodSpec.Builder addParameter(ParameterSpec parameterSpec) {
         this.parameters.add(parameterSpec);
         return this;
      }

      public MethodSpec.Builder addParameter(TypeName type, String name, Modifier... modifiers) {
         return this.addParameter(ParameterSpec.builder(type, name, modifiers).build());
      }

      public MethodSpec.Builder addParameter(Type type, String name, Modifier... modifiers) {
         return this.addParameter(TypeName.get(type), name, modifiers);
      }

      public MethodSpec.Builder varargs() {
         return this.varargs(true);
      }

      public MethodSpec.Builder varargs(boolean varargs) {
         this.varargs = varargs;
         return this;
      }

      public MethodSpec.Builder addExceptions(Iterable<? extends TypeName> exceptions) {
         Util.checkArgument(exceptions != null, "exceptions == null");

         for (TypeName exception : exceptions) {
            this.exceptions.add(exception);
         }

         return this;
      }

      public MethodSpec.Builder addException(TypeName exception) {
         this.exceptions.add(exception);
         return this;
      }

      public MethodSpec.Builder addException(Type exception) {
         return this.addException(TypeName.get(exception));
      }

      public MethodSpec.Builder addNamedCode(String format, Map<String, ?> args) {
         this.code.addNamed(format, args);
         return this;
      }

      public MethodSpec.Builder addCode(String format, Object... args) {
         this.code.add(format, args);
         return this;
      }

      public MethodSpec.Builder addCode(CodeBlock codeBlock) {
         this.code.add(codeBlock);
         return this;
      }

      public MethodSpec.Builder addComment(String format, Object... args) {
         this.code.add("// " + format + "\n", args);
         return this;
      }

      public MethodSpec.Builder defaultValue(String format, Object... args) {
         return this.defaultValue(CodeBlock.of(format, args));
      }

      public MethodSpec.Builder defaultValue(CodeBlock codeBlock) {
         Util.checkState(this.defaultValue == null, "defaultValue was already set");
         this.defaultValue = Util.checkNotNull(codeBlock, "codeBlock == null");
         return this;
      }

      public MethodSpec.Builder beginControlFlow(String controlFlow, Object... args) {
         this.code.beginControlFlow(controlFlow, args);
         return this;
      }

      public MethodSpec.Builder beginControlFlow(CodeBlock codeBlock) {
         return this.beginControlFlow("$L", codeBlock);
      }

      public MethodSpec.Builder nextControlFlow(String controlFlow, Object... args) {
         this.code.nextControlFlow(controlFlow, args);
         return this;
      }

      public MethodSpec.Builder nextControlFlow(CodeBlock codeBlock) {
         return this.nextControlFlow("$L", codeBlock);
      }

      public MethodSpec.Builder endControlFlow() {
         this.code.endControlFlow();
         return this;
      }

      public MethodSpec.Builder endControlFlow(String controlFlow, Object... args) {
         this.code.endControlFlow(controlFlow, args);
         return this;
      }

      public MethodSpec.Builder endControlFlow(CodeBlock codeBlock) {
         return this.endControlFlow("$L", codeBlock);
      }

      public MethodSpec.Builder addStatement(String format, Object... args) {
         this.code.addStatement(format, args);
         return this;
      }

      public MethodSpec.Builder addStatement(CodeBlock codeBlock) {
         this.code.addStatement(codeBlock);
         return this;
      }

      public MethodSpec build() {
         return new MethodSpec(this);
      }
   }
}
