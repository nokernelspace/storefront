package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

public final class TypeSpec {
   private final TypeSpec.Kind kind;
   private final String name;
   private final CodeBlock anonymousTypeArguments;
   private final CodeBlock javadoc;
   private final List<AnnotationSpec> annotations;
   private final Set<Modifier> modifiers;
   private final List<TypeVariableName> typeVariables;
   private final TypeName superclass;
   private final List<TypeName> superinterfaces;
   private final List<TypeName> permittedSubclasses;
   private final Map<String, TypeSpec> enumConstants;
   private final List<FieldSpec> fieldSpecs;
   private final CodeBlock staticBlock;
   private final CodeBlock initializerBlock;
   private final List<MethodSpec> methodSpecs;
   private final List<TypeSpec> typeSpecs;
   private final Set<String> nestedTypesSimpleNames;
   private final List<Element> originatingElements;
   private final Set<String> alwaysQualifiedNames;
   private final MethodSpec recordConstructor;

   private TypeSpec(TypeSpec.Builder builder) {
      this.kind = builder.kind;
      this.name = builder.name;
      this.anonymousTypeArguments = builder.anonymousTypeArguments;
      this.javadoc = builder.javadoc.build();
      this.annotations = Util.immutableList(builder.annotations);
      this.modifiers = Util.immutableSet(builder.modifiers);
      this.typeVariables = Util.immutableList(builder.typeVariables);
      this.superclass = builder.superclass;
      this.superinterfaces = Util.immutableList(builder.superinterfaces);
      this.permittedSubclasses = Util.immutableList(builder.permittedSubclasses);
      this.enumConstants = Util.immutableMap(builder.enumConstants);
      this.fieldSpecs = Util.immutableList(builder.fieldSpecs);
      this.staticBlock = builder.staticBlock.build();
      this.initializerBlock = builder.initializerBlock.build();
      this.methodSpecs = Util.immutableList(builder.methodSpecs);
      this.typeSpecs = Util.immutableList(builder.typeSpecs);
      this.alwaysQualifiedNames = Util.immutableSet(builder.alwaysQualifiedNames);
      this.recordConstructor = builder.recordConstructor;
      this.nestedTypesSimpleNames = new HashSet<>();
      List<Element> originatingElementsMutable = new ArrayList<>();
      originatingElementsMutable.addAll(builder.originatingElements);

      for (TypeSpec typeSpec : builder.typeSpecs) {
         this.nestedTypesSimpleNames.add(typeSpec.name);
         originatingElementsMutable.addAll(typeSpec.originatingElements);
      }

      this.originatingElements = Util.immutableList(originatingElementsMutable);
   }

   private TypeSpec(TypeSpec type) {
      this.kind = type.kind;
      this.name = type.name;
      this.anonymousTypeArguments = null;
      this.javadoc = type.javadoc;
      this.annotations = Collections.emptyList();
      this.modifiers = Collections.emptySet();
      this.typeVariables = Collections.emptyList();
      this.superclass = null;
      this.superinterfaces = Collections.emptyList();
      this.permittedSubclasses = Collections.emptyList();
      this.enumConstants = Collections.emptyMap();
      this.fieldSpecs = Collections.emptyList();
      this.staticBlock = type.staticBlock;
      this.initializerBlock = type.initializerBlock;
      this.methodSpecs = Collections.emptyList();
      this.typeSpecs = Collections.emptyList();
      this.originatingElements = Collections.emptyList();
      this.nestedTypesSimpleNames = Collections.emptySet();
      this.alwaysQualifiedNames = Collections.emptySet();
      this.recordConstructor = null;
   }

   public TypeSpec.Kind kind() {
      return this.kind;
   }

   public String name() {
      return this.name;
   }

   public CodeBlock anonymousTypeArguments() {
      return this.anonymousTypeArguments;
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

   public TypeName superclass() {
      return this.superclass;
   }

   public List<TypeName> superinterfaces() {
      return this.superinterfaces;
   }

   public Map<String, TypeSpec> enumConstants() {
      return this.enumConstants;
   }

   public List<FieldSpec> fieldSpecs() {
      return this.fieldSpecs;
   }

   public CodeBlock staticBlock() {
      return this.staticBlock;
   }

   public CodeBlock initializerBlock() {
      return this.initializerBlock;
   }

   public List<MethodSpec> methodSpecs() {
      return this.methodSpecs;
   }

   public List<TypeSpec> typeSpecs() {
      return this.typeSpecs;
   }

   public Set<String> nestedTypesSimpleNames() {
      return this.nestedTypesSimpleNames;
   }

   public List<Element> originatingElements() {
      return this.originatingElements;
   }

   public Set<String> alwaysQualifiedNames() {
      return this.alwaysQualifiedNames;
   }

   public static TypeSpec.Builder classBuilder(String name) {
      return new TypeSpec.Builder(TypeSpec.Kind.CLASS, Util.checkNotNull(name, "name == null"), null);
   }

   public static TypeSpec.Builder classBuilder(ClassName className) {
      return classBuilder(Util.checkNotNull(className, "className == null").simpleName());
   }

   public static TypeSpec.Builder recordBuilder(String name) {
      return new TypeSpec.Builder(TypeSpec.Kind.RECORD, Util.checkNotNull(name, "name == null"), null);
   }

   public static TypeSpec.Builder recordBuilder(ClassName className) {
      return recordBuilder(Util.checkNotNull(className, "className == null").simpleName());
   }

   public static TypeSpec.Builder interfaceBuilder(String name) {
      return new TypeSpec.Builder(TypeSpec.Kind.INTERFACE, Util.checkNotNull(name, "name == null"), null);
   }

   public static TypeSpec.Builder interfaceBuilder(ClassName className) {
      return interfaceBuilder(Util.checkNotNull(className, "className == null").simpleName());
   }

   public static TypeSpec.Builder enumBuilder(String name) {
      return new TypeSpec.Builder(TypeSpec.Kind.ENUM, Util.checkNotNull(name, "name == null"), null);
   }

   public static TypeSpec.Builder enumBuilder(ClassName className) {
      return enumBuilder(Util.checkNotNull(className, "className == null").simpleName());
   }

   public static TypeSpec.Builder anonymousClassBuilder(String typeArgumentsFormat, Object... args) {
      return anonymousClassBuilder(CodeBlock.of(typeArgumentsFormat, args));
   }

   public static TypeSpec.Builder anonymousClassBuilder(CodeBlock typeArguments) {
      return new TypeSpec.Builder(TypeSpec.Kind.CLASS, null, typeArguments);
   }

   public static TypeSpec.Builder annotationBuilder(String name) {
      return new TypeSpec.Builder(TypeSpec.Kind.ANNOTATION, Util.checkNotNull(name, "name == null"), null);
   }

   public static TypeSpec.Builder annotationBuilder(ClassName className) {
      return annotationBuilder(Util.checkNotNull(className, "className == null").simpleName());
   }

   public TypeSpec.Builder toBuilder() {
      TypeSpec.Builder builder = new TypeSpec.Builder(this.kind, this.name, this.anonymousTypeArguments);
      builder.javadoc.add(this.javadoc);
      builder.annotations.addAll(this.annotations);
      builder.modifiers.addAll(this.modifiers);
      builder.typeVariables.addAll(this.typeVariables);
      builder.superclass = this.superclass;
      builder.superinterfaces.addAll(this.superinterfaces);
      builder.permittedSubclasses.addAll(this.permittedSubclasses);
      builder.enumConstants.putAll(this.enumConstants);
      builder.fieldSpecs.addAll(this.fieldSpecs);
      builder.methodSpecs.addAll(this.methodSpecs);
      builder.typeSpecs.addAll(this.typeSpecs);
      builder.initializerBlock.add(this.initializerBlock);
      builder.staticBlock.add(this.staticBlock);
      builder.originatingElements.addAll(this.originatingElements);
      builder.alwaysQualifiedNames.addAll(this.alwaysQualifiedNames);
      return builder;
   }

   void emit(CodeWriter codeWriter, String enumName, Set<Modifier> implicitModifiers) throws IOException {
      int previousStatementLine = codeWriter.statementLine;
      codeWriter.statementLine = -1;

      try {
         if (enumName != null) {
            codeWriter.emitJavadoc(this.javadoc);
            codeWriter.emitAnnotations(this.annotations, false);
            codeWriter.emit("$L", enumName);
            if (!this.anonymousTypeArguments.formatParts().isEmpty()) {
               codeWriter.emit("(");
               codeWriter.emit(this.anonymousTypeArguments);
               codeWriter.emit(")");
            }

            if (this.fieldSpecs.isEmpty() && this.methodSpecs.isEmpty() && this.typeSpecs.isEmpty()) {
               return;
            }

            codeWriter.emit(" {\n");
         } else if (this.anonymousTypeArguments != null) {
            TypeName supertype = !this.superinterfaces.isEmpty() ? this.superinterfaces.get(0) : this.superclass;
            codeWriter.emit("new $T(", supertype);
            codeWriter.emit(this.anonymousTypeArguments);
            codeWriter.emit(") {\n");
         } else {
            codeWriter.pushType(new TypeSpec(this));
            if (this.recordConstructor != null) {
               codeWriter.emitJavadocWithParameters(this.javadoc, this.recordConstructor.parameters());
            } else {
               codeWriter.emitJavadoc(this.javadoc);
            }

            codeWriter.emitAnnotations(this.annotations, false);
            codeWriter.emitModifiers(this.modifiers, Util.union(implicitModifiers, this.kind.asMemberModifiers));
            codeWriter.emit("$L $L", this.kind.keyword, this.name);
            codeWriter.emitTypeVariables(this.typeVariables);
            if (this.kind == TypeSpec.Kind.RECORD) {
               if (this.recordConstructor != null) {
                  codeWriter.emitParameters(this.recordConstructor.parameters(), this.recordConstructor.varargs());
               } else {
                  codeWriter.emitParameters(List.of(), false);
               }
            }

            List<TypeName> extendsTypes;
            List<TypeName> implementsTypes;
            if (this.kind == TypeSpec.Kind.INTERFACE) {
               extendsTypes = this.superinterfaces;
               implementsTypes = Collections.emptyList();
            } else {
               extendsTypes = this.superclass.equals(ClassName.OBJECT) ? Collections.emptyList() : Collections.singletonList(this.superclass);
               implementsTypes = this.superinterfaces;
            }

            if (!extendsTypes.isEmpty()) {
               codeWriter.emit(" extends");
               this.typeList(codeWriter, extendsTypes);
            }

            if (!implementsTypes.isEmpty()) {
               codeWriter.emit(" implements");
               this.typeList(codeWriter, implementsTypes);
            }

            if (!this.permittedSubclasses.isEmpty()) {
               codeWriter.emit(" permits");
               boolean firstType = true;

               for (TypeName type : this.permittedSubclasses) {
                  if (!firstType) {
                     codeWriter.emit(",");
                  }

                  codeWriter.emit(" $T", type);
                  firstType = false;
               }
            }

            codeWriter.popType();
            codeWriter.emit(" {\n");
         }

         codeWriter.pushType(this);
         codeWriter.indent();
         boolean firstMember = true;
         boolean needsSeparator = this.kind == TypeSpec.Kind.ENUM && (!this.fieldSpecs.isEmpty() || !this.methodSpecs.isEmpty() || !this.typeSpecs.isEmpty());
         Iterator<Entry<String, TypeSpec>> i = this.enumConstants.entrySet().iterator();

         while (i.hasNext()) {
            Entry<String, TypeSpec> enumConstant = i.next();
            if (!firstMember) {
               codeWriter.emit("\n");
            }

            enumConstant.getValue().emit(codeWriter, enumConstant.getKey(), Collections.emptySet());
            firstMember = false;
            if (i.hasNext()) {
               codeWriter.emit(",\n");
            } else if (!needsSeparator) {
               codeWriter.emit("\n");
            }
         }

         if (needsSeparator) {
            codeWriter.emit(";\n");
         }

         for (FieldSpec fieldSpec : this.fieldSpecs) {
            if (fieldSpec.modifiers().contains(Modifier.STATIC)) {
               if (!firstMember) {
                  codeWriter.emit("\n");
               }

               fieldSpec.emit(codeWriter, this.kind.implicitFieldModifiers);
               firstMember = false;
            }
         }

         if (!this.staticBlock.isEmpty()) {
            if (!firstMember) {
               codeWriter.emit("\n");
            }

            codeWriter.emit(this.staticBlock);
            firstMember = false;
         }

         for (FieldSpec fieldSpec : this.fieldSpecs) {
            if (!fieldSpec.modifiers().contains(Modifier.STATIC)) {
               if (!firstMember) {
                  codeWriter.emit("\n");
               }

               fieldSpec.emit(codeWriter, this.kind.implicitFieldModifiers);
               firstMember = false;
            }
         }

         if (!this.initializerBlock.isEmpty()) {
            if (!firstMember) {
               codeWriter.emit("\n");
            }

            codeWriter.emit(this.initializerBlock);
            firstMember = false;
         }

         if (this.recordConstructor != null && !this.recordConstructor.code().isEmpty()) {
            if (!firstMember) {
               codeWriter.emit("\n");
            }

            this.recordConstructor.emit(codeWriter, this.name, this.kind.implicitMethodModifiers);
            firstMember = false;
         }

         for (MethodSpec methodSpec : this.methodSpecs) {
            if (methodSpec.isConstructor()) {
               if (!firstMember) {
                  codeWriter.emit("\n");
               }

               methodSpec.emit(codeWriter, this.name, this.kind.implicitMethodModifiers);
               firstMember = false;
            }
         }

         for (MethodSpec methodSpec : this.methodSpecs) {
            if (!methodSpec.isConstructor()) {
               if (!firstMember) {
                  codeWriter.emit("\n");
               }

               methodSpec.emit(codeWriter, this.name, this.kind.implicitMethodModifiers);
               firstMember = false;
            }
         }

         for (TypeSpec typeSpec : this.typeSpecs) {
            if (!firstMember) {
               codeWriter.emit("\n");
            }

            typeSpec.emit(codeWriter, null, this.kind.implicitTypeModifiers);
            firstMember = false;
         }

         codeWriter.unindent();
         codeWriter.popType();
         codeWriter.popTypeVariables(this.typeVariables);
         codeWriter.emit("}");
         if (enumName == null && this.anonymousTypeArguments == null) {
            codeWriter.emit("\n");
         }
      } finally {
         codeWriter.statementLine = previousStatementLine;
      }
   }

   private void typeList(CodeWriter codeWriter, List<TypeName> types) throws IOException {
      boolean firstType = true;
      boolean wrap = types.size() > 3;
      codeWriter.indent(2);

      for (TypeName type : types) {
         if (!firstType) {
            codeWriter.emit(",");
            codeWriter.emit(wrap ? "\n" : " ");
         } else {
            codeWriter.emit(" ");
         }

         codeWriter.emit("$T", type);
         firstType = false;
      }

      codeWriter.unindent(2);
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
         this.emit(codeWriter, null, Collections.emptySet());
         return out.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static final class Builder {
      private final TypeSpec.Kind kind;
      private final String name;
      private final CodeBlock anonymousTypeArguments;
      private MethodSpec recordConstructor;
      private final CodeBlock.Builder javadoc = CodeBlock.builder();
      private TypeName superclass = ClassName.OBJECT;
      private final CodeBlock.Builder staticBlock = CodeBlock.builder();
      private final CodeBlock.Builder initializerBlock = CodeBlock.builder();
      private final Map<String, TypeSpec> enumConstants = new LinkedHashMap<>();
      private final List<AnnotationSpec> annotations = new ArrayList<>();
      private final List<Modifier> modifiers = new ArrayList<>();
      private final List<TypeVariableName> typeVariables = new ArrayList<>();
      private final List<TypeName> superinterfaces = new ArrayList<>();
      private final List<TypeName> permittedSubclasses = new ArrayList<>();
      private final List<FieldSpec> fieldSpecs = new ArrayList<>();
      private final List<MethodSpec> methodSpecs = new ArrayList<>();
      private final List<TypeSpec> typeSpecs = new ArrayList<>();
      private final List<Element> originatingElements = new ArrayList<>();
      private final Set<String> alwaysQualifiedNames = new LinkedHashSet<>();

      private Builder(TypeSpec.Kind kind, String name, CodeBlock anonymousTypeArguments) {
         Util.checkArgument(name == null || SourceVersion.isName(name), "not a valid name: %s", name);
         this.kind = kind;
         this.name = name;
         this.anonymousTypeArguments = anonymousTypeArguments;
      }

      public TypeSpec.Builder addJavadoc(String format, Object... args) {
         this.javadoc.add(format, args);
         return this;
      }

      public TypeSpec.Builder addJavadoc(CodeBlock block) {
         this.javadoc.add(block);
         return this;
      }

      public TypeSpec.Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
         Util.checkArgument(annotationSpecs != null, "annotationSpecs == null");

         for (AnnotationSpec annotationSpec : annotationSpecs) {
            this.annotations.add(annotationSpec);
         }

         return this;
      }

      public TypeSpec.Builder addAnnotation(AnnotationSpec annotationSpec) {
         Util.checkNotNull(annotationSpec, "annotationSpec == null");
         this.annotations.add(annotationSpec);
         return this;
      }

      public TypeSpec.Builder addAnnotation(ClassName annotation) {
         return this.addAnnotation(AnnotationSpec.builder(annotation).build());
      }

      public TypeSpec.Builder addAnnotation(Class<?> annotation) {
         return this.addAnnotation(ClassName.get(annotation));
      }

      public TypeSpec.Builder addModifiers(Modifier... modifiers) {
         Collections.addAll(this.modifiers, modifiers);
         return this;
      }

      public TypeSpec.Builder addTypeVariables(Iterable<TypeVariableName> typeVariables) {
         Util.checkArgument(typeVariables != null, "typeVariables == null");

         for (TypeVariableName typeVariable : typeVariables) {
            this.typeVariables.add(typeVariable);
         }

         return this;
      }

      public TypeSpec.Builder addTypeVariable(TypeVariableName typeVariable) {
         this.typeVariables.add(typeVariable);
         return this;
      }

      public TypeSpec.Builder superclass(TypeName superclass) {
         Util.checkState(this.kind == TypeSpec.Kind.CLASS, "only classes have super classes, not " + this.kind);
         Util.checkState(this.superclass == ClassName.OBJECT, "superclass already set to " + this.superclass);
         Util.checkArgument(!superclass.isPrimitive(), "superclass may not be a primitive");
         this.superclass = superclass;
         return this;
      }

      public TypeSpec.Builder superclass(Type superclass) {
         return this.superclass(superclass, true);
      }

      public TypeSpec.Builder superclass(Type superclass, boolean avoidNestedTypeNameClashes) {
         this.superclass(TypeName.get(superclass));
         if (avoidNestedTypeNameClashes) {
            Class<?> clazz = getRawType(superclass);
            if (clazz != null) {
               this.avoidClashesWithNestedClasses(clazz);
            }
         }

         return this;
      }

      public TypeSpec.Builder superclass(TypeMirror superclass) {
         return this.superclass(superclass, true);
      }

      public TypeSpec.Builder superclass(TypeMirror superclass, boolean avoidNestedTypeNameClashes) {
         this.superclass(TypeName.get(superclass));
         if (avoidNestedTypeNameClashes && superclass instanceof DeclaredType declaredType) {
            TypeElement superInterfaceElement = (TypeElement)declaredType.asElement();
            this.avoidClashesWithNestedClasses(superInterfaceElement);
         }

         return this;
      }

      public TypeSpec.Builder addSuperinterfaces(Iterable<? extends TypeName> superinterfaces) {
         Util.checkArgument(superinterfaces != null, "superinterfaces == null");

         for (TypeName superinterface : superinterfaces) {
            this.addSuperinterface(superinterface);
         }

         return this;
      }

      public TypeSpec.Builder addSuperinterface(TypeName superinterface) {
         Util.checkArgument(superinterface != null, "superinterface == null");
         this.superinterfaces.add(superinterface);
         return this;
      }

      public TypeSpec.Builder addSuperinterface(Type superinterface) {
         return this.addSuperinterface(superinterface, true);
      }

      public TypeSpec.Builder addSuperinterface(Type superinterface, boolean avoidNestedTypeNameClashes) {
         this.addSuperinterface(TypeName.get(superinterface));
         if (avoidNestedTypeNameClashes) {
            Class<?> clazz = getRawType(superinterface);
            if (clazz != null) {
               this.avoidClashesWithNestedClasses(clazz);
            }
         }

         return this;
      }

      public TypeSpec.Builder addSuperinterface(TypeMirror superinterface) {
         return this.addSuperinterface(superinterface, true);
      }

      public TypeSpec.Builder addSuperinterface(TypeMirror superinterface, boolean avoidNestedTypeNameClashes) {
         this.addSuperinterface(TypeName.get(superinterface));
         if (avoidNestedTypeNameClashes && superinterface instanceof DeclaredType declaredType) {
            TypeElement superInterfaceElement = (TypeElement)declaredType.asElement();
            this.avoidClashesWithNestedClasses(superInterfaceElement);
         }

         return this;
      }

      public TypeSpec.Builder recordConstructor(MethodSpec recordConstructor) {
         if (this.kind != TypeSpec.Kind.RECORD) {
            throw new UnsupportedOperationException(this.kind + " can't have record constructor");
         }

         this.recordConstructor = recordConstructor;
         return this;
      }

      public TypeSpec.Builder addPermittedSubclasses(Iterable<? extends TypeName> permittedSubclasses) {
         Util.checkArgument(permittedSubclasses != null, "permittedSubclasses == null");

         for (TypeName permittedSubclass : permittedSubclasses) {
            this.addPermittedSubclass(permittedSubclass);
         }

         return this;
      }

      public TypeSpec.Builder addPermittedSubclass(TypeName permittedSubclass) {
         Util.checkState(
            this.kind == TypeSpec.Kind.CLASS || this.kind == TypeSpec.Kind.INTERFACE,
            "only classes and interfaces can have permitted subclasses, not " + this.kind
         );
         Util.checkArgument(permittedSubclass != null, "permittedSubclass == null");
         this.permittedSubclasses.add(permittedSubclass);
         return this;
      }

      public TypeSpec.Builder addPermittedSubclass(Type permittedSubclass) {
         return this.addPermittedSubclass(permittedSubclass, true);
      }

      public TypeSpec.Builder addPermittedSubclass(Type permittedSubclass, boolean avoidNestedTypeNameClashes) {
         this.addPermittedSubclass(TypeName.get(permittedSubclass));
         if (avoidNestedTypeNameClashes) {
            Class<?> clazz = getRawType(permittedSubclass);
            if (clazz != null) {
               this.avoidClashesWithNestedClasses(clazz);
            }
         }

         return this;
      }

      public TypeSpec.Builder addPermittedSubclass(TypeMirror permittedSubclass) {
         return this.addPermittedSubclass(permittedSubclass, true);
      }

      public TypeSpec.Builder addPermittedSubclass(TypeMirror permittedSubclass, boolean avoidNestedTypeNameClashes) {
         this.addPermittedSubclass(TypeName.get(permittedSubclass));
         if (avoidNestedTypeNameClashes && permittedSubclass instanceof DeclaredType declaredType) {
            TypeElement superInterfaceElement = (TypeElement)declaredType.asElement();
            this.avoidClashesWithNestedClasses(superInterfaceElement);
         }

         return this;
      }

      public TypeSpec.Builder addEnumConstant(String name) {
         return this.addEnumConstant(name, TypeSpec.anonymousClassBuilder("").build());
      }

      public TypeSpec.Builder addEnumConstant(String name, TypeSpec typeSpec) {
         this.enumConstants.put(name, typeSpec);
         return this;
      }

      public TypeSpec.Builder addFields(Iterable<FieldSpec> fieldSpecs) {
         Util.checkArgument(fieldSpecs != null, "fieldSpecs == null");

         for (FieldSpec fieldSpec : fieldSpecs) {
            this.addField(fieldSpec);
         }

         return this;
      }

      public TypeSpec.Builder addField(FieldSpec fieldSpec) {
         this.fieldSpecs.add(fieldSpec);
         return this;
      }

      public TypeSpec.Builder addField(TypeName type, String name, Modifier... modifiers) {
         return this.addField(FieldSpec.builder(type, name, modifiers).build());
      }

      public TypeSpec.Builder addField(Type type, String name, Modifier... modifiers) {
         return this.addField(TypeName.get(type), name, modifiers);
      }

      public TypeSpec.Builder addStaticBlock(CodeBlock block) {
         this.staticBlock.beginControlFlow("static").add(block).endControlFlow();
         return this;
      }

      public TypeSpec.Builder addInitializerBlock(CodeBlock block) {
         if (this.kind != TypeSpec.Kind.CLASS && this.kind != TypeSpec.Kind.ENUM) {
            throw new UnsupportedOperationException(this.kind + " can't have initializer blocks");
         }

         this.initializerBlock.add("{\n").indent().add(block).unindent().add("}\n");
         return this;
      }

      public TypeSpec.Builder addMethods(Iterable<MethodSpec> methodSpecs) {
         Util.checkArgument(methodSpecs != null, "methodSpecs == null");

         for (MethodSpec methodSpec : methodSpecs) {
            this.addMethod(methodSpec);
         }

         return this;
      }

      public TypeSpec.Builder addMethod(MethodSpec methodSpec) {
         this.methodSpecs.add(methodSpec);
         return this;
      }

      public TypeSpec.Builder addTypes(Iterable<TypeSpec> typeSpecs) {
         Util.checkArgument(typeSpecs != null, "typeSpecs == null");

         for (TypeSpec typeSpec : typeSpecs) {
            this.addType(typeSpec);
         }

         return this;
      }

      public TypeSpec.Builder addType(TypeSpec typeSpec) {
         this.typeSpecs.add(typeSpec);
         return this;
      }

      public TypeSpec.Builder addOriginatingElement(Element originatingElement) {
         this.originatingElements.add(originatingElement);
         return this;
      }

      public TypeSpec.Builder alwaysQualify(String... simpleNames) {
         Util.checkArgument(simpleNames != null, "simpleNames == null");

         for (String name : simpleNames) {
            Util.checkArgument(name != null, "null entry in simpleNames array: %s", Arrays.toString(simpleNames));
            this.alwaysQualifiedNames.add(name);
         }

         return this;
      }

      public TypeSpec.Builder avoidClashesWithNestedClasses(TypeElement typeElement) {
         Util.checkArgument(typeElement != null, "typeElement == null");

         for (TypeElement nestedType : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
            this.alwaysQualify(nestedType.getSimpleName().toString());
         }

         TypeMirror superclass = typeElement.getSuperclass();
         if (!(superclass instanceof NoType) && superclass instanceof DeclaredType declaredType) {
            TypeElement superclassElement = (TypeElement)declaredType.asElement();
            this.avoidClashesWithNestedClasses(superclassElement);
         }

         for (TypeMirror superinterface : typeElement.getInterfaces()) {
            if (superinterface instanceof DeclaredType declaredType) {
               TypeElement superinterfaceElement = (TypeElement)declaredType.asElement();
               this.avoidClashesWithNestedClasses(superinterfaceElement);
            }
         }

         return this;
      }

      public TypeSpec.Builder avoidClashesWithNestedClasses(Class<?> clazz) {
         Util.checkArgument(clazz != null, "clazz == null");

         for (Class<?> nestedType : clazz.getDeclaredClasses()) {
            this.alwaysQualify(nestedType.getSimpleName());
         }

         Class<?> superclass = clazz.getSuperclass();
         if (superclass != null && !Object.class.equals(superclass)) {
            this.avoidClashesWithNestedClasses(superclass);
         }

         for (Class<?> superinterface : clazz.getInterfaces()) {
            this.avoidClashesWithNestedClasses(superinterface);
         }

         return this;
      }

      public TypeSpec build() {
         for (AnnotationSpec annotationSpec : this.annotations) {
            Util.checkNotNull(annotationSpec, "annotationSpec == null");
         }

         if (!this.modifiers.isEmpty()) {
            Util.checkState(this.anonymousTypeArguments == null, "forbidden on anonymous types.");

            for (Modifier modifier : this.modifiers) {
               Util.checkArgument(modifier != null, "modifiers contain null");
            }
         }

         if (this.recordConstructor != null) {
            for (ParameterSpec recordComponent : this.recordConstructor.parameters()) {
               Util.checkArgument(recordComponent.modifiers().isEmpty(), "record components must not have modifiers");
            }
         }

         for (TypeName superinterface : this.superinterfaces) {
            Util.checkArgument(superinterface != null, "superinterfaces contains null");
         }

         for (TypeName superinterface : this.permittedSubclasses) {
            Util.checkArgument(superinterface != null, "permittedSubclasses contains null");
         }

         if (!this.typeVariables.isEmpty()) {
            Util.checkState(this.anonymousTypeArguments == null, "typevariables are forbidden on anonymous types.");

            for (TypeVariableName typeVariableName : this.typeVariables) {
               Util.checkArgument(typeVariableName != null, "typeVariables contain null");
            }
         }

         for (Entry<String, TypeSpec> enumConstant : this.enumConstants.entrySet()) {
            Util.checkState(this.kind == TypeSpec.Kind.ENUM, "%s is not enum", this.name);
            Util.checkArgument(enumConstant.getValue().anonymousTypeArguments != null, "enum constants must have anonymous type arguments");
            Util.checkArgument(SourceVersion.isName(this.name), "not a valid enum constant: %s", this.name);
         }

         for (FieldSpec fieldSpec : this.fieldSpecs) {
            if (this.kind == TypeSpec.Kind.INTERFACE || this.kind == TypeSpec.Kind.ANNOTATION) {
               Util.requireExactlyOneOf(fieldSpec.modifiers(), Modifier.PUBLIC, Modifier.PRIVATE);
               Set<Modifier> check = EnumSet.of(Modifier.STATIC, Modifier.FINAL);
               Util.checkState(fieldSpec.modifiers().containsAll(check), "%s %s.%s requires modifiers %s", this.kind, this.name, fieldSpec.name(), check);
            }

            if (this.kind == TypeSpec.Kind.RECORD) {
               Util.checkState(fieldSpec.modifiers().contains(Modifier.STATIC), "%s %s.%s must be static", this.kind, this.name, fieldSpec.name());
            }
         }

         for (MethodSpec methodSpec : this.methodSpecs) {
            if (this.kind == TypeSpec.Kind.INTERFACE) {
               Util.requireExactlyOneOf(methodSpec.modifiers(), Modifier.PUBLIC, Modifier.PRIVATE);
               if (methodSpec.modifiers().contains(Modifier.PRIVATE)) {
                  Util.checkState(
                     !methodSpec.modifiers().contains(Modifier.DEFAULT), "%s %s.%s cannot be private and default", this.kind, this.name, methodSpec.name()
                  );
                  Util.checkState(
                     !methodSpec.modifiers().contains(Modifier.ABSTRACT), "%s %s.%s cannot be private and abstract", this.kind, this.name, methodSpec.name()
                  );
               } else {
                  Util.requireExactlyOneOf(methodSpec.modifiers(), Modifier.ABSTRACT, Modifier.STATIC, Modifier.DEFAULT);
               }
            } else if (this.kind == TypeSpec.Kind.ANNOTATION) {
               Util.checkState(
                  methodSpec.modifiers().equals(this.kind.implicitMethodModifiers),
                  "%s %s.%s requires modifiers %s",
                  this.kind,
                  this.name,
                  methodSpec.name(),
                  this.kind.implicitMethodModifiers
               );
            }

            if (this.kind != TypeSpec.Kind.ANNOTATION) {
               Util.checkState(methodSpec.defaultValue() == null, "%s %s.%s cannot have a default value", this.kind, this.name, methodSpec.name());
            }

            if (this.kind != TypeSpec.Kind.INTERFACE) {
               Util.checkState(!methodSpec.modifiers().contains(Modifier.DEFAULT), "%s %s.%s cannot be default", this.kind, this.name, methodSpec.name());
            }

            if (this.kind == TypeSpec.Kind.RECORD) {
               Util.checkState(!methodSpec.modifiers().contains(Modifier.NATIVE), "%s %s.%s cannot be native", this.kind, this.name, methodSpec.name());
            }
         }

         for (TypeSpec typeSpec : this.typeSpecs) {
            Util.checkArgument(
               typeSpec.modifiers.containsAll(this.kind.implicitTypeModifiers),
               "%s %s.%s requires modifiers %s",
               this.kind,
               this.name,
               typeSpec.name,
               this.kind.implicitTypeModifiers
            );
         }
         boolean isAbstract = switch (this.kind) {
            case CLASS, RECORD -> this.modifiers.contains(Modifier.ABSTRACT);
            case INTERFACE, ENUM, ANNOTATION -> true;
         };

         for (MethodSpec methodSpec : this.methodSpecs) {
            Util.checkArgument(
               isAbstract || !methodSpec.modifiers().contains(Modifier.ABSTRACT),
               "non-abstract type %s cannot declare abstract method %s",
               this.name,
               methodSpec.name()
            );
         }

         boolean superclassIsObject = this.superclass.equals(ClassName.OBJECT);
         int interestingSupertypeCount = (superclassIsObject ? 0 : 1) + this.superinterfaces.size();
         Util.checkArgument(this.anonymousTypeArguments == null || interestingSupertypeCount <= 1, "anonymous type has too many supertypes");
         boolean isSealed = this.modifiers.contains(Modifier.SEALED);
         Util.checkArgument(isSealed || this.permittedSubclasses.isEmpty(), "non-sealed type %s cannot declare permitted subclasses", this.name);
         return new TypeSpec(this);
      }

      private static Class<?> getRawType(Type type) {
         if (type instanceof Class<?> classType) {
            return classType;
         } else {
            return type instanceof ParameterizedType parameterizedType ? getRawType(parameterizedType.getRawType()) : null;
         }
      }
   }

   public enum Kind {
      CLASS(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), "class"),
      RECORD(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), "record"),
      INTERFACE(
         Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)),
         Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.ABSTRACT)),
         Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)),
         Util.immutableSet(Collections.singletonList(Modifier.STATIC)),
         "interface"
      ),
      ENUM(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.singleton(Modifier.STATIC), "enum"),
      ANNOTATION(
         Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)),
         Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.ABSTRACT)),
         Util.immutableSet(Arrays.asList(Modifier.PUBLIC, Modifier.STATIC)),
         Util.immutableSet(Collections.singletonList(Modifier.STATIC)),
         "@interface"
      );

      private final Set<Modifier> implicitFieldModifiers;
      private final Set<Modifier> implicitMethodModifiers;
      private final Set<Modifier> implicitTypeModifiers;
      private final Set<Modifier> asMemberModifiers;
      private final String keyword;

      Kind(
         Set<Modifier> implicitFieldModifiers,
         Set<Modifier> implicitMethodModifiers,
         Set<Modifier> implicitTypeModifiers,
         Set<Modifier> asMemberModifiers,
         String keyword
      ) {
         this.implicitFieldModifiers = implicitFieldModifiers;
         this.implicitMethodModifiers = implicitMethodModifiers;
         this.implicitTypeModifiers = implicitTypeModifiers;
         this.asMemberModifiers = asMemberModifiers;
         this.keyword = keyword;
      }
   }
}
